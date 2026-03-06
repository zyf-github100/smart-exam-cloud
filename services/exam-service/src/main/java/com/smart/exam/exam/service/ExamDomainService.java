package com.smart.exam.exam.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.core.event.ExamSubmittedEvent;
import com.smart.exam.common.core.id.SnowflakeIdGenerator;
import com.smart.exam.exam.config.AntiCheatProperties;
import com.smart.exam.exam.config.RabbitConfig;
import com.smart.exam.exam.dto.CreateExamRequest;
import com.smart.exam.exam.dto.ReportAntiCheatEventRequest;
import com.smart.exam.exam.dto.SaveAnswersRequest;
import com.smart.exam.exam.entity.AnswerEntity;
import com.smart.exam.exam.entity.ExamEntity;
import com.smart.exam.exam.entity.ExamSessionEntity;
import com.smart.exam.exam.entity.ExamTargetEntity;
import com.smart.exam.exam.entity.SessionRiskEventEntity;
import com.smart.exam.exam.entity.SessionRiskSummaryEntity;
import com.smart.exam.exam.mapper.AnswerMapper;
import com.smart.exam.exam.mapper.ExamMapper;
import com.smart.exam.exam.mapper.ExamSessionMapper;
import com.smart.exam.exam.mapper.ExamTargetMapper;
import com.smart.exam.exam.mapper.QuestionReadMapper;
import com.smart.exam.exam.mapper.SessionRiskEventMapper;
import com.smart.exam.exam.mapper.SessionRiskSummaryMapper;
import com.smart.exam.exam.mapper.UserReadMapper;
import com.smart.exam.exam.model.AntiCheatRiskEvent;
import com.smart.exam.exam.model.AntiCheatRiskSummary;
import com.smart.exam.exam.model.AssignedExam;
import com.smart.exam.exam.model.AnswerItem;
import com.smart.exam.exam.model.Exam;
import com.smart.exam.exam.model.ExamPaper;
import com.smart.exam.exam.model.ExamPaperQuestion;
import com.smart.exam.exam.model.ExamStatus;
import com.smart.exam.exam.model.SessionAnswer;
import com.smart.exam.exam.model.SessionStatus;
import com.smart.exam.exam.model.read.PaperQuestionSnapshot;
import com.smart.exam.exam.model.read.PaperSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ExamDomainService {

    private static final Logger log = LoggerFactory.getLogger(ExamDomainService.class);
    private static final Duration SUBMIT_LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration START_LOCK_TTL = Duration.ofSeconds(8);
    private static final Duration EXAM_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration CREATE_EXAM_DEDUP_TTL = Duration.ofSeconds(5);
    private static final String SUBMIT_LOCK_PREFIX = "exam:submit:lock:";
    private static final String START_LOCK_PREFIX = "exam:start:lock:";
    private static final String EXAM_CACHE_PREFIX = "exam:detail:";
    private static final String CREATE_EXAM_DEDUP_PREFIX = "exam:create:dedup:";
    private static final String ANSWER_SPLIT_REGEX = "[,\\uFF0C\\s]+";

    private final SnowflakeIdGenerator idGenerator;
    private final RabbitTemplate rabbitTemplate;
    private final ExamMapper examMapper;
    private final ExamTargetMapper examTargetMapper;
    private final ExamSessionMapper examSessionMapper;
    private final AnswerMapper answerMapper;
    private final QuestionReadMapper questionReadMapper;
    private final UserReadMapper userReadMapper;
    private final SessionRiskEventMapper sessionRiskEventMapper;
    private final SessionRiskSummaryMapper sessionRiskSummaryMapper;
    private final AntiCheatRuleEngine antiCheatRuleEngine;
    private final AntiCheatProperties antiCheatProperties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ExamDomainService(SnowflakeIdGenerator idGenerator,
                             ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
                             ExamMapper examMapper,
                             ExamTargetMapper examTargetMapper,
                             ExamSessionMapper examSessionMapper,
                             AnswerMapper answerMapper,
                             QuestionReadMapper questionReadMapper,
                             UserReadMapper userReadMapper,
                             SessionRiskEventMapper sessionRiskEventMapper,
                             SessionRiskSummaryMapper sessionRiskSummaryMapper,
                             AntiCheatRuleEngine antiCheatRuleEngine,
                             AntiCheatProperties antiCheatProperties,
                             StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper) {
        this.idGenerator = idGenerator;
        this.rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        this.examMapper = examMapper;
        this.examTargetMapper = examTargetMapper;
        this.examSessionMapper = examSessionMapper;
        this.answerMapper = answerMapper;
        this.questionReadMapper = questionReadMapper;
        this.userReadMapper = userReadMapper;
        this.sessionRiskEventMapper = sessionRiskEventMapper;
        this.sessionRiskSummaryMapper = sessionRiskSummaryMapper;
        this.antiCheatRuleEngine = antiCheatRuleEngine;
        this.antiCheatProperties = antiCheatProperties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Exam createExam(CreateExamRequest request, String userId, String role) {
        protectDuplicateCreate(userId, request);

        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "startTime must be before endTime");
        }

        long paperId = parseLong("paperId", request.getPaperId());
        boolean adminRole = "ADMIN".equalsIgnoreCase(String.valueOf(role).trim());
        PaperSnapshot ownedPaper = adminRole
                ? questionReadMapper.selectPaperById(paperId)
                : questionReadMapper.selectPaperByIdAndCreatedBy(paperId, parseLong("userId", userId));
        if (ownedPaper == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Paper not found: " + request.getPaperId());
        }

        LinkedHashSet<Long> targetStudentIds = parseTargetStudentIds(request.getStudentIds());
        validateTargetStudents(targetStudentIds);

        ExamEntity entity = new ExamEntity();
        entity.setId(idGenerator.nextId());
        entity.setPaperId(paperId);
        entity.setTitle(request.getTitle().trim());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setAntiCheatLevel(request.getAntiCheatLevel());
        entity.setStatus(resolveExamStatus(entity.getStartTime(), entity.getEndTime(), LocalDateTime.now()).name());
        entity.setCreatedBy(parseLong("createdBy", userId));
        examMapper.insert(entity);

        batchInsertExamTargets(entity.getId(), parseLong("createdBy", userId), targetStudentIds);

        Exam exam = toExam(entity);
        exam.setTargetStudentCount(targetStudentIds.size());
        exam.setStudentIds(targetStudentIds.stream().map(String::valueOf).toList());
        putExamCache(exam);
        return exam;
    }

    @Transactional
    public Map<String, Object> startExam(String examId, String userId, String role, String ip) {
        Exam exam = getExam(examId);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime()) || !now.isBefore(exam.getEndTime())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Exam is not in active window");
        }

        long examLongId = parseLong("examId", examId);
        long userLongId = parseLong("userId", userId);
        boolean adminRole = "ADMIN".equalsIgnoreCase(String.valueOf(role).trim());
        if (!adminRole) {
            boolean assigned = examTargetMapper.selectCount(
                    Wrappers.lambdaQuery(ExamTargetEntity.class)
                            .eq(ExamTargetEntity::getExamId, examLongId)
                            .eq(ExamTargetEntity::getStudentId, userLongId)
            ) > 0;
            if (!assigned) {
                throw new BizException(ErrorCode.FORBIDDEN, "Exam is not assigned to current student");
            }
        }

        String startLockKey = START_LOCK_PREFIX + examLongId + ":" + userLongId;
        if (!acquireSimpleLock(startLockKey, START_LOCK_TTL)) {
            throw new BizException(ErrorCode.CONFLICT, "Duplicate start request");
        }
        ExamSessionEntity sessionEntity;
        try {
            sessionEntity = findLatestSession(examLongId, userLongId);
            if (sessionEntity == null) {
                sessionEntity = buildNewSession(examLongId, userLongId, now, ip);
                try {
                    examSessionMapper.insert(sessionEntity);
                } catch (DuplicateKeyException ex) {
                    sessionEntity = findLatestSession(examLongId, userLongId);
                    if (sessionEntity == null) {
                        throw new BizException(ErrorCode.CONFLICT, "Failed to create session, please retry");
                    }
                }
            }
        } finally {
            releaseSimpleLock(startLockKey);
        }
        validateSessionStartable(sessionEntity);

        long timeLimitSeconds = Math.max(1, Duration.between(now, exam.getEndTime()).toSeconds());
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", String.valueOf(sessionEntity.getId()));
        payload.put("serverTime", now);
        payload.put("timeLimitSeconds", timeLimitSeconds);
        return payload;
    }

    public List<AssignedExam> listAssignedExams(String userId, String role) {
        long studentId = parseLong("studentId", userId);
        boolean adminRole = "ADMIN".equalsIgnoreCase(String.valueOf(role).trim());
        List<ExamEntity> exams;
        if (adminRole) {
            exams = examMapper.selectList(
                    Wrappers.lambdaQuery(ExamEntity.class)
                            .orderByDesc(ExamEntity::getStartTime)
                            .last("limit 100")
            );
        } else {
            List<ExamTargetEntity> targets = examTargetMapper.selectList(
                    Wrappers.lambdaQuery(ExamTargetEntity.class)
                            .eq(ExamTargetEntity::getStudentId, studentId)
                            .orderByDesc(ExamTargetEntity::getAssignedAt)
            );
            if (targets.isEmpty()) {
                return List.of();
            }
            LinkedHashSet<Long> examIds = new LinkedHashSet<>();
            for (ExamTargetEntity target : targets) {
                if (target.getExamId() != null) {
                    examIds.add(target.getExamId());
                }
            }
            if (examIds.isEmpty()) {
                return List.of();
            }
            exams = examMapper.selectBatchIds(examIds);
        }

        if (exams == null || exams.isEmpty()) {
            return List.of();
        }

        Map<Long, ExamEntity> examMap = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (ExamEntity exam : exams) {
            if (exam == null || exam.getId() == null) {
                continue;
            }
            syncExamStatusIfNeeded(exam, now);
            examMap.put(exam.getId(), exam);
        }
        if (examMap.isEmpty()) {
            return List.of();
        }

        Set<Long> examIds = examMap.keySet();
        List<ExamSessionEntity> sessions = examSessionMapper.selectList(
                Wrappers.lambdaQuery(ExamSessionEntity.class)
                        .eq(ExamSessionEntity::getUserId, studentId)
                        .in(ExamSessionEntity::getExamId, examIds)
                        .orderByDesc(ExamSessionEntity::getId)
        );
        Map<Long, ExamSessionEntity> latestSessionByExamId = new HashMap<>();
        for (ExamSessionEntity session : sessions) {
            if (session == null || session.getExamId() == null) {
                continue;
            }
            latestSessionByExamId.putIfAbsent(session.getExamId(), session);
        }

        List<AssignedExam> result = new ArrayList<>(examMap.size());
        for (ExamEntity exam : examMap.values()) {
            result.add(toAssignedExam(exam, latestSessionByExamId.get(exam.getId())));
        }
        result.sort(
                Comparator.comparing(AssignedExam::getStartTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AssignedExam::getExamId, Comparator.nullsLast(Comparator.reverseOrder()))
        );
        return result;
    }

    public List<Exam> listPublishedExams(String userId, String role) {
        boolean adminRole = "ADMIN".equalsIgnoreCase(String.valueOf(role).trim());
        var query = Wrappers.lambdaQuery(ExamEntity.class);
        if (!adminRole) {
            query.eq(ExamEntity::getCreatedBy, parseLong("userId", userId));
        }
        query.orderByDesc(ExamEntity::getStartTime)
                .orderByDesc(ExamEntity::getId)
                .last("limit 200");

        List<ExamEntity> exams = examMapper.selectList(query);
        if (exams == null || exams.isEmpty()) {
            return List.of();
        }

        Map<Long, ExamEntity> examMap = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (ExamEntity exam : exams) {
            if (exam == null || exam.getId() == null) {
                continue;
            }
            syncExamStatusIfNeeded(exam, now);
            examMap.put(exam.getId(), exam);
        }
        if (examMap.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> targetCountMap = new HashMap<>();
        List<ExamTargetEntity> targets = examTargetMapper.selectList(
                Wrappers.lambdaQuery(ExamTargetEntity.class)
                        .in(ExamTargetEntity::getExamId, examMap.keySet())
        );
        for (ExamTargetEntity target : targets) {
            if (target == null || target.getExamId() == null) {
                continue;
            }
            targetCountMap.merge(target.getExamId(), 1, Integer::sum);
        }

        List<Exam> result = new ArrayList<>(examMap.size());
        for (ExamEntity exam : examMap.values()) {
            Exam payload = toExam(exam);
            payload.setTargetStudentCount(targetCountMap.getOrDefault(exam.getId(), 0));
            result.add(payload);
        }
        result.sort(
                Comparator.comparing(Exam::getStartTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Exam::getId, Comparator.nullsLast(Comparator.reverseOrder()))
        );
        return result;
    }

    public ExamPaper getSessionPaper(String sessionId, String userId) {
        long sessionLongId = parseLong("sessionId", sessionId);
        ExamSessionEntity session = getSessionEntity(sessionLongId);
        validateSessionOwner(session, userId);

        ExamEntity exam = examMapper.selectById(session.getExamId());
        if (exam == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam not found");
        }

        PaperSnapshot paperSnapshot = questionReadMapper.selectPaperById(exam.getPaperId());
        if (paperSnapshot == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Paper not found");
        }

        List<PaperQuestionSnapshot> questionSnapshots =
                questionReadMapper.selectPaperQuestionsByPaperId(exam.getPaperId());
        if (questionSnapshots.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Paper contains no questions");
        }

        ExamPaper paper = new ExamPaper();
        paper.setSessionId(String.valueOf(session.getId()));
        paper.setExamId(String.valueOf(exam.getId()));
        paper.setPaperId(String.valueOf(paperSnapshot.getId()));
        paper.setPaperName(paperSnapshot.getName());
        paper.setTotalScore(paperSnapshot.getTotalScore());
        paper.setTimeLimitMinutes(paperSnapshot.getTimeLimitMinutes());
        paper.setQuestions(questionSnapshots.stream().map(this::toExamPaperQuestion).toList());
        return paper;
    }

    public List<SessionAnswer> listSessionAnswers(String sessionId, String userId) {
        long sessionLongId = parseLong("sessionId", sessionId);
        ExamSessionEntity session = getSessionEntity(sessionLongId);
        validateSessionOwner(session, userId);

        List<AnswerEntity> entities = answerMapper.selectBySessionIdOrdered(sessionLongId);
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }

        List<SessionAnswer> result = new ArrayList<>(entities.size());
        for (AnswerEntity entity : entities) {
            if (entity == null || entity.getQuestionId() == null) {
                continue;
            }
            SessionAnswer item = new SessionAnswer();
            item.setQuestionId(String.valueOf(entity.getQuestionId()));
            item.setAnswerContent(readJsonObject(entity.getAnswerContent()));
            item.setMarkedForReview(entity.getIsMarkedForReview() != null && entity.getIsMarkedForReview() == 1);
            item.setUpdatedAt(entity.getUpdatedAt());
            result.add(item);
        }
        return result;
    }

    @Transactional
    public void saveAnswers(String sessionId, SaveAnswersRequest request, String userId) {
        long sessionLongId = parseLong("sessionId", sessionId);
        ExamSessionEntity session = getSessionEntity(sessionLongId);

        validateSessionOwner(session, userId);
        if (!SessionStatus.IN_PROGRESS.name().equals(session.getStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Session is not editable");
        }

        ExamEntity exam = examMapper.selectById(session.getExamId());
        if (exam == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam not found");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(exam.getEndTime())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Exam has ended, answer saving is disabled");
        }

        Map<Long, PaperQuestionSnapshot> paperQuestionMap = loadPaperQuestionMap(exam.getPaperId());
        Set<Long> answeredQuestionIds = new HashSet<>();

        for (AnswerItem item : request.getAnswers()) {
            if (item == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Answer item cannot be null");
            }
            long questionLongId = parseLong("questionId", item.getQuestionId());
            if (!answeredQuestionIds.add(questionLongId)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Duplicate questionId in request: " + questionLongId);
            }

            PaperQuestionSnapshot paperQuestion = paperQuestionMap.get(questionLongId);
            if (paperQuestion == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Question does not belong to current paper: " + questionLongId);
            }
            Object normalizedAnswer = normalizeAnswerContent(paperQuestion.getType(), item.getAnswerContent());

            AnswerEntity entity = answerMapper.selectOne(
                    Wrappers.lambdaQuery(AnswerEntity.class)
                            .eq(AnswerEntity::getSessionId, sessionLongId)
                            .eq(AnswerEntity::getQuestionId, questionLongId)
                            .last("limit 1")
            );

            if (entity == null) {
                entity = new AnswerEntity();
                entity.setSessionId(sessionLongId);
                entity.setQuestionId(questionLongId);
                entity.setAnswerContent(writeAsJson(normalizedAnswer));
                entity.setIsMarkedForReview(Boolean.TRUE.equals(item.getMarkedForReview()) ? 1 : 0);
                answerMapper.insert(entity);
            } else {
                entity.setAnswerContent(writeAsJson(normalizedAnswer));
                entity.setIsMarkedForReview(Boolean.TRUE.equals(item.getMarkedForReview()) ? 1 : 0);
                entity.setUpdatedAt(LocalDateTime.now());
                answerMapper.updateById(entity);
            }
        }

        session.setLastSaveTime(now);
        examSessionMapper.updateById(session);
    }

    @Transactional
    public Map<String, Object> submit(String sessionId, String userId) {
        if (!acquireSubmitLock(sessionId)) {
            throw new BizException(ErrorCode.CONFLICT, "Duplicate submit request");
        }

        long sessionLongId = parseLong("sessionId", sessionId);
        ExamSessionEntity session = getSessionEntity(sessionLongId);

        if (!String.valueOf(session.getUserId()).equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Session does not belong to current user");
        }
        if (!SessionStatus.IN_PROGRESS.name().equals(session.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Session already submitted");
        }

        ExamEntity exam = examMapper.selectById(session.getExamId());
        if (exam == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam not found");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean deadlineExceeded = !now.isBefore(exam.getEndTime());
        LocalDateTime effectiveSubmitTime = deadlineExceeded ? exam.getEndTime() : now;

        session.setStatus(SessionStatus.SUBMITTED.name());
        session.setSubmitTime(effectiveSubmitTime);
        examSessionMapper.updateById(session);

        ExamSubmittedEvent event = new ExamSubmittedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setExamId(String.valueOf(session.getExamId()));
        event.setSessionId(String.valueOf(session.getId()));
        event.setUserId(String.valueOf(session.getUserId()));
        event.setSubmittedAt(OffsetDateTime.now());
        publishSubmittedEvent(event);

        return Map.of(
                "sessionId", sessionId,
                "status", session.getStatus(),
                "submittedAt", session.getSubmitTime(),
                "deadlineExceeded", deadlineExceeded
        );
    }

    @Transactional
    public Map<String, Object> reportAntiCheatEvent(String sessionId,
                                                    String userId,
                                                    String clientIp,
                                                    ReportAntiCheatEventRequest request) {
        long sessionLongId = parseLong("sessionId", sessionId);
        ExamSessionEntity session = getSessionEntity(sessionLongId);
        if (!String.valueOf(session.getUserId()).equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Session does not belong to current user");
        }
        if (!SessionStatus.IN_PROGRESS.name().equals(session.getStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Session is not active");
        }

        ExamEntity exam = examMapper.selectById(session.getExamId());
        if (exam == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam not found");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime eventTime = request.getEventTime() == null ? now : request.getEventTime();
        if (eventTime.isAfter(now.plusMinutes(antiCheatProperties.getMaxFutureSkewMinutes()))) {
            throw new BizException(ErrorCode.BAD_REQUEST, "eventTime is too far in future");
        }
        String eventType = antiCheatRuleEngine.normalizeEventType(request.getEventType());
        SessionRiskSummaryEntity currentSummary = sessionRiskSummaryMapper.selectById(sessionLongId);
        int eventScore = antiCheatRuleEngine.calculateScore(eventType, exam.getAntiCheatLevel(), currentSummary, eventTime);

        SessionRiskEventEntity eventEntity = new SessionRiskEventEntity();
        eventEntity.setId(idGenerator.nextId());
        eventEntity.setSessionId(sessionLongId);
        eventEntity.setExamId(session.getExamId());
        eventEntity.setUserId(session.getUserId());
        eventEntity.setEventType(eventType);
        eventEntity.setEventTime(eventTime);
        eventEntity.setEventScore(eventScore);
        eventEntity.setPayloadJson(writeAsJson(request.getMetadata() == null ? Collections.emptyMap() : request.getMetadata()));
        eventEntity.setClientIp(clientIp);
        eventEntity.setCreatedAt(LocalDateTime.now());
        sessionRiskEventMapper.insert(eventEntity);

        boolean insertSummary = currentSummary == null;
        int riskScore = eventScore;
        int eventCount = 1;
        if (insertSummary) {
            currentSummary = new SessionRiskSummaryEntity();
            currentSummary.setSessionId(sessionLongId);
            currentSummary.setExamId(session.getExamId());
            currentSummary.setUserId(session.getUserId());
        } else {
            riskScore += safeInt(currentSummary.getRiskScore());
            eventCount += safeInt(currentSummary.getEventCount());
        }
        currentSummary.setRiskScore(riskScore);
        currentSummary.setEventCount(eventCount);
        currentSummary.setRiskLevel(antiCheatRuleEngine.resolveRiskLevel(riskScore));
        currentSummary.setLastEventType(eventType);
        currentSummary.setLastEventTime(eventTime);
        currentSummary.setUpdatedAt(LocalDateTime.now());
        if (insertSummary) {
            sessionRiskSummaryMapper.insert(currentSummary);
        } else {
            sessionRiskSummaryMapper.updateById(currentSummary);
        }

        if ("SWITCH_SCREEN".equals(eventType)) {
            session.setSwitchScreenCount(safeInt(session.getSwitchScreenCount()) + 1);
            examSessionMapper.updateById(session);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("accepted", true);
        payload.put("sessionId", sessionId);
        payload.put("eventId", String.valueOf(eventEntity.getId()));
        payload.put("eventType", eventType);
        payload.put("eventScore", eventScore);
        payload.put("riskSummary", toRiskSummary(currentSummary));
        return payload;
    }

    public Map<String, Object> getSessionRisk(String sessionId, String operatorId, String role) {
        long sessionLongId = parseLong("sessionId", sessionId);
        ExamSessionEntity session = getSessionEntity(sessionLongId);
        ExamEntity exam = examMapper.selectById(session.getExamId());
        if (exam == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam not found");
        }
        ensureExamAccessibleForTeacher(exam, operatorId, role);
        SessionRiskSummaryEntity summary = sessionRiskSummaryMapper.selectById(sessionLongId);
        if (summary == null) {
            summary = new SessionRiskSummaryEntity();
            summary.setSessionId(sessionLongId);
            summary.setExamId(session.getExamId());
            summary.setUserId(session.getUserId());
            summary.setRiskScore(0);
            summary.setEventCount(0);
            summary.setRiskLevel("LOW");
            summary.setUpdatedAt(LocalDateTime.now());
        }

        List<SessionRiskEventEntity> events = sessionRiskEventMapper.selectList(
                Wrappers.lambdaQuery(SessionRiskEventEntity.class)
                        .eq(SessionRiskEventEntity::getSessionId, sessionLongId)
                        .orderByDesc(SessionRiskEventEntity::getEventTime)
                        .last("limit " + antiCheatProperties.getRecentEventsLimit())
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("summary", toRiskSummary(summary));
        payload.put("events", events.stream().map(this::toRiskEvent).toList());
        return payload;
    }

    public Map<String, Object> listExamRisks(String examId, String riskLevel, Long page, Long size, String operatorId, String role) {
        long examLongId = parseLong("examId", examId);
        ExamEntity exam = examMapper.selectById(examLongId);
        if (exam == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam not found");
        }
        ensureExamAccessibleForTeacher(exam, operatorId, role);
        long safePage = normalizePage(page);
        long safeSize = normalizeSize(size);
        long offset = (safePage - 1) * safeSize;
        String normalizedRiskLevel = normalizeRiskLevel(riskLevel);

        Long total = sessionRiskSummaryMapper.selectCount(
                Wrappers.lambdaQuery(SessionRiskSummaryEntity.class)
                        .eq(SessionRiskSummaryEntity::getExamId, examLongId)
                        .eq(StringUtils.hasText(normalizedRiskLevel), SessionRiskSummaryEntity::getRiskLevel, normalizedRiskLevel)
        );
        List<SessionRiskSummaryEntity> records = List.of();
        if (total != null && total > 0) {
            records = sessionRiskSummaryMapper.selectList(
                    Wrappers.lambdaQuery(SessionRiskSummaryEntity.class)
                            .eq(SessionRiskSummaryEntity::getExamId, examLongId)
                            .eq(StringUtils.hasText(normalizedRiskLevel), SessionRiskSummaryEntity::getRiskLevel, normalizedRiskLevel)
                            .orderByDesc(SessionRiskSummaryEntity::getRiskScore)
                            .orderByDesc(SessionRiskSummaryEntity::getUpdatedAt)
                            .last("limit " + offset + "," + safeSize)
            );
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("page", safePage);
        payload.put("size", safeSize);
        payload.put("total", total == null ? 0L : total);
        payload.put("records", records.stream().map(this::toRiskSummary).toList());
        return payload;
    }

    public Exam getExam(String examId) {
        LocalDateTime now = LocalDateTime.now();
        String cacheKey = EXAM_CACHE_PREFIX + examId;
        Exam cached = getCache(cacheKey, Exam.class);
        if (cached != null && cached.getStatus() == resolveExamStatus(cached.getStartTime(), cached.getEndTime(), now)) {
            return cached;
        }

        ExamEntity entity = examMapper.selectById(parseLong("examId", examId));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam not found");
        }
        syncExamStatusIfNeeded(entity, now);

        Exam exam = toExam(entity);
        putExamCache(exam);
        return exam;
    }

    @Transactional
    public int syncExamStatuses() {
        LocalDateTime now = LocalDateTime.now();
        int updatedCount = 0;

        List<ExamEntity> toRunning = examMapper.selectList(
                Wrappers.lambdaQuery(ExamEntity.class)
                        .eq(ExamEntity::getStatus, ExamStatus.NOT_STARTED.name())
                        .le(ExamEntity::getStartTime, now)
                        .gt(ExamEntity::getEndTime, now)
        );
        for (ExamEntity entity : toRunning) {
            entity.setStatus(ExamStatus.RUNNING.name());
            examMapper.updateById(entity);
            evictExamCache(String.valueOf(entity.getId()));
            updatedCount++;
        }

        List<ExamEntity> toFinished = examMapper.selectList(
                Wrappers.lambdaQuery(ExamEntity.class)
                        .in(ExamEntity::getStatus, ExamStatus.NOT_STARTED.name(), ExamStatus.RUNNING.name())
                        .le(ExamEntity::getEndTime, now)
        );
        for (ExamEntity entity : toFinished) {
            entity.setStatus(ExamStatus.FINISHED.name());
            examMapper.updateById(entity);
            evictExamCache(String.valueOf(entity.getId()));
            updatedCount++;
        }

        return updatedCount;
    }

    public List<Long> listExpiredInProgressSessionIds(long limit) {
        long safeLimit = Math.max(1, Math.min(limit, 500));
        List<Long> ids = examSessionMapper.selectExpiredInProgressSessionIds(LocalDateTime.now(), safeLimit);
        return ids == null ? List.of() : ids;
    }

    @Transactional
    public boolean forceSubmitExpiredSession(Long sessionId) {
        if (sessionId == null) {
            return false;
        }
        ExamSessionEntity session = getSessionEntity(sessionId);
        if (!SessionStatus.IN_PROGRESS.name().equals(session.getStatus())) {
            return false;
        }

        ExamEntity exam = examMapper.selectById(session.getExamId());
        if (exam == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam not found");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getEndTime())) {
            return false;
        }
        LocalDateTime effectiveSubmitTime = exam.getEndTime();
        int updated = examSessionMapper.updateStatusIfMatched(
                sessionId,
                SessionStatus.IN_PROGRESS.name(),
                SessionStatus.FORCE_SUBMITTED.name(),
                effectiveSubmitTime
        );
        if (updated < 1) {
            return false;
        }

        ExamSubmittedEvent event = new ExamSubmittedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setExamId(String.valueOf(session.getExamId()));
        event.setSessionId(String.valueOf(session.getId()));
        event.setUserId(String.valueOf(session.getUserId()));
        event.setSubmittedAt(OffsetDateTime.now());
        publishSubmittedEvent(event);
        return true;
    }

    private void validateSessionOwner(ExamSessionEntity session, String userId) {
        if (!String.valueOf(session.getUserId()).equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Session does not belong to current user");
        }
    }

    private ExamSessionEntity findLatestSession(long examId, long userId) {
        return examSessionMapper.selectOne(
                Wrappers.lambdaQuery(ExamSessionEntity.class)
                        .eq(ExamSessionEntity::getExamId, examId)
                        .eq(ExamSessionEntity::getUserId, userId)
                        .orderByDesc(ExamSessionEntity::getId)
                        .last("limit 1")
        );
    }

    private ExamSessionEntity buildNewSession(long examId, long userId, LocalDateTime now, String ip) {
        ExamSessionEntity sessionEntity = new ExamSessionEntity();
        sessionEntity.setId(idGenerator.nextId());
        sessionEntity.setExamId(examId);
        sessionEntity.setUserId(userId);
        sessionEntity.setStartTime(now);
        sessionEntity.setSubmitTime(null);
        sessionEntity.setStatus(SessionStatus.IN_PROGRESS.name());
        sessionEntity.setIpAtStart(ip);
        sessionEntity.setSwitchScreenCount(0);
        sessionEntity.setLastSaveTime(now);
        return sessionEntity;
    }

    private void validateSessionStartable(ExamSessionEntity sessionEntity) {
        if (SessionStatus.IN_PROGRESS.name().equals(sessionEntity.getStatus())) {
            return;
        }
        if (SessionStatus.SUBMITTED.name().equals(sessionEntity.getStatus())
                || SessionStatus.FORCE_SUBMITTED.name().equals(sessionEntity.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Exam has already been submitted by current student");
        }
        throw new BizException(ErrorCode.CONFLICT, "Session status is not startable: " + sessionEntity.getStatus());
    }

    private void ensureExamAccessibleForTeacher(ExamEntity exam, String operatorId, String role) {
        if (isAdminRole(role)) {
            return;
        }
        long teacherId = parseLong("userId", operatorId);
        if (exam.getCreatedBy() == null || !exam.getCreatedBy().equals(teacherId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Exam does not belong to current teacher");
        }
    }

    private boolean isAdminRole(String role) {
        return "ADMIN".equalsIgnoreCase(String.valueOf(role).trim());
    }

    private LinkedHashSet<Long> parseTargetStudentIds(List<String> rawStudentIds) {
        if (rawStudentIds == null || rawStudentIds.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "studentIds cannot be empty");
        }
        LinkedHashSet<Long> studentIds = new LinkedHashSet<>();
        for (String rawStudentId : rawStudentIds) {
            String value = rawStudentId == null ? "" : rawStudentId.trim();
            if (!StringUtils.hasText(value)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "studentIds contains blank value");
            }
            studentIds.add(parseLong("studentId", value));
        }
        if (studentIds.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "studentIds cannot be empty");
        }
        if (studentIds.size() > 500) {
            throw new BizException(ErrorCode.BAD_REQUEST, "studentIds exceeds max size: 500");
        }
        return studentIds;
    }

    private void validateTargetStudents(LinkedHashSet<Long> studentIds) {
        List<Long> validStudentIds = userReadMapper.selectActiveStudentIds(new ArrayList<>(studentIds));
        Set<Long> validIdSet = new HashSet<>(validStudentIds);
        List<Long> invalidIds = new ArrayList<>();
        for (Long studentId : studentIds) {
            if (!validIdSet.contains(studentId)) {
                invalidIds.add(studentId);
            }
        }
        if (!invalidIds.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid studentIds: " + invalidIds);
        }
    }

    private void batchInsertExamTargets(Long examId, Long assignedBy, LinkedHashSet<Long> studentIds) {
        LocalDateTime now = LocalDateTime.now();
        for (Long studentId : studentIds) {
            ExamTargetEntity entity = new ExamTargetEntity();
            entity.setExamId(examId);
            entity.setStudentId(studentId);
            entity.setAssignedBy(assignedBy);
            entity.setAssignedAt(now);
            examTargetMapper.insert(entity);
        }
    }

    private Map<Long, PaperQuestionSnapshot> loadPaperQuestionMap(Long paperId) {
        List<PaperQuestionSnapshot> questions = questionReadMapper.selectPaperQuestionsByPaperId(paperId);
        if (questions.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Paper contains no questions");
        }

        Map<Long, PaperQuestionSnapshot> result = new HashMap<>();
        for (PaperQuestionSnapshot question : questions) {
            Long questionId = question.getQuestionId();
            if (questionId == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Paper questionId cannot be null");
            }
            if (result.putIfAbsent(questionId, question) != null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Duplicate questionId in paper: " + questionId);
            }
        }
        return result;
    }

    private Object normalizeAnswerContent(String rawQuestionType, Object answerContent) {
        String questionType = normalizeQuestionType(rawQuestionType);
        if (isBlankAnswer(answerContent)) {
            return defaultEmptyAnswer(questionType);
        }

        return switch (questionType) {
            case "SINGLE" -> normalizeSingleAnswer(answerContent);
            case "MULTI" -> normalizeMultiAnswer(answerContent);
            case "JUDGE" -> normalizeJudgeAnswer(answerContent);
            case "FILL", "SHORT" -> normalizeTextAnswer(answerContent);
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported question type: " + rawQuestionType);
        };
    }

    private boolean isBlankAnswer(Object answerContent) {
        if (answerContent == null) {
            return true;
        }
        if (answerContent instanceof String text) {
            return !StringUtils.hasText(text);
        }
        if (answerContent instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (answerContent instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private Object defaultEmptyAnswer(String questionType) {
        if ("MULTI".equals(questionType)) {
            return List.of();
        }
        return "";
    }

    private String normalizeSingleAnswer(Object answerContent) {
        List<String> tokens = parseChoiceTokens(answerContent);
        if (tokens.size() != 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, "SINGLE answer must contain exactly one option");
        }
        return tokens.get(0);
    }

    private List<String> normalizeMultiAnswer(Object answerContent) {
        return parseChoiceTokens(answerContent);
    }

    private List<String> parseChoiceTokens(Object answerContent) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        if (answerContent instanceof Collection<?> collection) {
            for (Object value : collection) {
                String token = normalizeChoiceToken(value);
                if (StringUtils.hasText(token)) {
                    tokens.add(token);
                }
            }
        } else {
            String scalar = String.valueOf(answerContent).trim();
            if (!StringUtils.hasText(scalar)) {
                return List.of();
            }
            String[] pieces = scalar.split(ANSWER_SPLIT_REGEX);
            for (String piece : pieces) {
                String token = normalizeChoiceToken(piece);
                if (StringUtils.hasText(token)) {
                    tokens.add(token);
                }
            }
        }

        if (tokens.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Choice answer cannot be blank");
        }
        return new ArrayList<>(tokens);
    }

    private String normalizeChoiceToken(Object rawToken) {
        String token = rawToken == null ? "" : String.valueOf(rawToken).trim().toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(token)) {
            return "";
        }
        if (token.matches(".*[,\\uFF0C\\s]+.*")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Choice option token cannot contain separator characters");
        }
        return token;
    }

    private Object normalizeJudgeAnswer(Object answerContent) {
        if (answerContent instanceof Boolean boolValue) {
            return boolValue;
        }
        String rawValue = String.valueOf(answerContent).trim().toLowerCase(Locale.ROOT);
        return switch (rawValue) {
            case "true", "1", "t", "yes", "y" -> true;
            case "false", "0", "f", "no", "n" -> false;
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "JUDGE answer must be true or false");
        };
    }

    private String normalizeTextAnswer(Object answerContent) {
        if (answerContent instanceof List<?> || answerContent instanceof Map<?, ?>) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Text answer must be scalar");
        }
        String text = String.valueOf(answerContent).trim();
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text;
    }

    private ExamPaperQuestion toExamPaperQuestion(PaperQuestionSnapshot snapshot) {
        String questionType = normalizeQuestionType(snapshot.getType());

        ExamPaperQuestion question = new ExamPaperQuestion();
        question.setQuestionId(snapshot.getQuestionId() == null ? null : String.valueOf(snapshot.getQuestionId()));
        question.setType(questionType);
        question.setStem(snapshot.getStem());
        question.setScore(snapshot.getScore());
        question.setOrderNo(snapshot.getOrderNo());

        List<Map<String, Object>> options = parsePaperOptions(snapshot.getOptionsJson());
        if (isChoiceType(questionType) && options.size() < 2) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Choice question must contain at least two options");
        }
        question.setOptions(isChoiceType(questionType) ? options : List.of());
        return question;
    }

    private List<Map<String, Object>> parsePaperOptions(String rawOptionsJson) {
        if (!StringUtils.hasText(rawOptionsJson)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> options = objectMapper.readValue(
                    rawOptionsJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );
            if (options == null || options.isEmpty()) {
                return List.of();
            }
            List<Map<String, Object>> normalized = new ArrayList<>(options.size());
            Set<String> keys = new HashSet<>();
            for (Map<String, Object> option : options) {
                if (option == null) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "Question option is invalid");
                }
                String rawKey = String.valueOf(option.getOrDefault("key", "")).trim();
                String rawText = String.valueOf(option.getOrDefault("text", "")).trim();
                if (!StringUtils.hasText(rawKey) || !StringUtils.hasText(rawText)) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "Question option is invalid");
                }
                String key = rawKey.toUpperCase(Locale.ROOT);
                if (!keys.add(key)) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "Duplicate question option key: " + key);
                }
                Map<String, Object> item = new HashMap<>();
                item.put("key", key);
                item.put("text", rawText);
                normalized.add(item);
            }
            return normalized;
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to parse question options");
        }
    }

    private boolean isChoiceType(String questionType) {
        return "SINGLE".equals(questionType) || "MULTI".equals(questionType);
    }

    private String normalizeQuestionType(String rawQuestionType) {
        if (!StringUtils.hasText(rawQuestionType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Question type is missing");
        }
        String type = rawQuestionType.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "SINGLE", "MULTI", "JUDGE", "FILL", "SHORT" -> type;
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported question type: " + rawQuestionType);
        };
    }

    private ExamSessionEntity getSessionEntity(long sessionId) {
        ExamSessionEntity entity = examSessionMapper.selectById(sessionId);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam session not found");
        }
        return entity;
    }

    private void protectDuplicateCreate(String userId, CreateExamRequest request) {
        String dedupKey = CREATE_EXAM_DEDUP_PREFIX + userId + ":" + sha256(writeAsJson(request));
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", CREATE_EXAM_DEDUP_TTL);
            if (Boolean.FALSE.equals(success)) {
                throw new BizException(ErrorCode.CONFLICT, "Duplicate create request");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Create exam dedup check failed", ex);
        }
    }

    private boolean acquireSubmitLock(String sessionId) {
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    SUBMIT_LOCK_PREFIX + sessionId,
                    "1",
                    SUBMIT_LOCK_TTL
            );
            return Boolean.TRUE.equals(locked);
        } catch (Exception ex) {
            log.warn("Submit lock unavailable, fallback allow submit, sessionId={}", sessionId, ex);
            return true;
        }
    }

    private boolean acquireSimpleLock(String lockKey, Duration ttl) {
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", ttl);
            return Boolean.TRUE.equals(locked);
        } catch (Exception ex) {
            log.warn("Lock unavailable, fallback allow, key={}", lockKey, ex);
            return true;
        }
    }

    private void releaseSimpleLock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (Exception ex) {
            log.warn("Lock release failed, key={}", lockKey, ex);
        }
    }

    private void publishSubmittedEvent(ExamSubmittedEvent event) {
        if (rabbitTemplate == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Message broker is unavailable, submit aborted");
        }
        try {
            rabbitTemplate.convertAndSend(
                    RabbitConfig.EXAM_EXCHANGE,
                    RabbitConfig.EXAM_SUBMITTED_ROUTING_KEY,
                    event,
                    new CorrelationData(event.getEventId())
            );
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to publish submit event");
        }
    }

    private Exam toExam(ExamEntity entity) {
        Exam exam = new Exam();
        exam.setId(String.valueOf(entity.getId()));
        exam.setPaperId(String.valueOf(entity.getPaperId()));
        exam.setTitle(entity.getTitle());
        exam.setStartTime(entity.getStartTime());
        exam.setEndTime(entity.getEndTime());
        exam.setAntiCheatLevel(entity.getAntiCheatLevel());
        exam.setStatus(ExamStatus.valueOf(entity.getStatus()));
        exam.setCreatedBy(String.valueOf(entity.getCreatedBy()));
        return exam;
    }

    private AssignedExam toAssignedExam(ExamEntity exam, ExamSessionEntity session) {
        AssignedExam assignedExam = new AssignedExam();
        assignedExam.setExamId(String.valueOf(exam.getId()));
        assignedExam.setPaperId(String.valueOf(exam.getPaperId()));
        assignedExam.setTitle(exam.getTitle());
        assignedExam.setStartTime(exam.getStartTime());
        assignedExam.setEndTime(exam.getEndTime());
        assignedExam.setAntiCheatLevel(exam.getAntiCheatLevel());
        assignedExam.setStatus(ExamStatus.valueOf(exam.getStatus()));
        if (session != null) {
            assignedExam.setSessionId(String.valueOf(session.getId()));
            assignedExam.setSessionStatus(SessionStatus.valueOf(session.getStatus()));
            assignedExam.setSessionStartTime(session.getStartTime());
            assignedExam.setSessionSubmitTime(session.getSubmitTime());
        }
        return assignedExam;
    }

    private void syncExamStatusIfNeeded(ExamEntity entity, LocalDateTime now) {
        ExamStatus expected = resolveExamStatus(entity.getStartTime(), entity.getEndTime(), now);
        if (expected.name().equals(entity.getStatus())) {
            return;
        }
        entity.setStatus(expected.name());
        examMapper.updateById(entity);
        evictExamCache(String.valueOf(entity.getId()));
    }

    private ExamStatus resolveExamStatus(LocalDateTime startTime, LocalDateTime endTime, LocalDateTime now) {
        if (!now.isBefore(endTime)) {
            return ExamStatus.FINISHED;
        }
        if (!now.isBefore(startTime)) {
            return ExamStatus.RUNNING;
        }
        return ExamStatus.NOT_STARTED;
    }

    private void putExamCache(Exam exam) {
        putCache(EXAM_CACHE_PREFIX + exam.getId(), exam, EXAM_CACHE_TTL);
    }

    private void evictExamCache(String examId) {
        try {
            redisTemplate.delete(EXAM_CACHE_PREFIX + examId);
        } catch (Exception ex) {
            log.warn("Failed to evict exam cache, examId={}", examId, ex);
        }
    }

    private <T> T getCache(String key, Class<T> clazz) {
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            return objectMapper.readValue(raw, clazz);
        } catch (Exception ex) {
            log.warn("Failed to read cache, key={}", key, ex);
            return null;
        }
    }

    private void putCache(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize cache payload", ex);
        } catch (Exception ex) {
            log.warn("Failed to write cache, key={}", key, ex);
        }
    }

    private AntiCheatRiskSummary toRiskSummary(SessionRiskSummaryEntity entity) {
        AntiCheatRiskSummary summary = new AntiCheatRiskSummary();
        summary.setSessionId(entity.getSessionId() == null ? null : String.valueOf(entity.getSessionId()));
        summary.setExamId(entity.getExamId() == null ? null : String.valueOf(entity.getExamId()));
        summary.setUserId(entity.getUserId() == null ? null : String.valueOf(entity.getUserId()));
        summary.setRiskScore(safeInt(entity.getRiskScore()));
        summary.setRiskLevel(entity.getRiskLevel());
        summary.setEventCount(safeInt(entity.getEventCount()));
        summary.setLastEventType(entity.getLastEventType());
        summary.setLastEventTime(entity.getLastEventTime());
        summary.setUpdatedAt(entity.getUpdatedAt());
        return summary;
    }

    private AntiCheatRiskEvent toRiskEvent(SessionRiskEventEntity entity) {
        AntiCheatRiskEvent event = new AntiCheatRiskEvent();
        event.setId(entity.getId() == null ? null : String.valueOf(entity.getId()));
        event.setEventType(entity.getEventType());
        event.setEventTime(entity.getEventTime());
        event.setEventScore(safeInt(entity.getEventScore()));
        event.setMetadata(readJsonObject(entity.getPayloadJson()));
        event.setClientIp(entity.getClientIp());
        event.setCreatedAt(entity.getCreatedAt());
        return event;
    }

    private Object readJsonObject(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(rawJson, Object.class);
        } catch (Exception ex) {
            log.warn("Failed to deserialize anti-cheat payload", ex);
            return rawJson;
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long normalizePage(Long page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    private long normalizeSize(Long size) {
        if (size == null || size < 1) {
            return antiCheatProperties.getPageDefaultSize();
        }
        return Math.min(size, antiCheatProperties.getPageMaxSize());
    }

    private String normalizeRiskLevel(String riskLevel) {
        if (!StringUtils.hasText(riskLevel)) {
            return null;
        }
        String normalized = riskLevel.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LOW", "MEDIUM", "HIGH", "CRITICAL" -> normalized;
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "Invalid riskLevel: " + riskLevel);
        };
    }

    private String writeAsJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "JSON serialization failed");
        }
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "SHA-256 unavailable");
        }
    }

    private long parseLong(String fieldName, String rawValue) {
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid " + fieldName + ": " + rawValue);
        }
    }

}
