package com.smart.exam.exam.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.smart.exam.exam.entity.SessionRiskEventEntity;
import com.smart.exam.exam.entity.SessionRiskSummaryEntity;
import com.smart.exam.exam.mapper.AnswerMapper;
import com.smart.exam.exam.mapper.ExamMapper;
import com.smart.exam.exam.mapper.ExamSessionMapper;
import com.smart.exam.exam.mapper.SessionRiskEventMapper;
import com.smart.exam.exam.mapper.SessionRiskSummaryMapper;
import com.smart.exam.exam.model.AntiCheatRiskEvent;
import com.smart.exam.exam.model.AntiCheatRiskSummary;
import com.smart.exam.exam.model.AnswerItem;
import com.smart.exam.exam.model.Exam;
import com.smart.exam.exam.model.ExamStatus;
import com.smart.exam.exam.model.SessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ExamDomainService {

    private static final Logger log = LoggerFactory.getLogger(ExamDomainService.class);
    private static final Duration SUBMIT_LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration EXAM_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration CREATE_EXAM_DEDUP_TTL = Duration.ofSeconds(5);
    private static final String SUBMIT_LOCK_PREFIX = "exam:submit:lock:";
    private static final String EXAM_CACHE_PREFIX = "exam:detail:";
    private static final String CREATE_EXAM_DEDUP_PREFIX = "exam:create:dedup:";

    private final SnowflakeIdGenerator idGenerator;
    private final RabbitTemplate rabbitTemplate;
    private final ExamMapper examMapper;
    private final ExamSessionMapper examSessionMapper;
    private final AnswerMapper answerMapper;
    private final SessionRiskEventMapper sessionRiskEventMapper;
    private final SessionRiskSummaryMapper sessionRiskSummaryMapper;
    private final AntiCheatRuleEngine antiCheatRuleEngine;
    private final AntiCheatProperties antiCheatProperties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ExamDomainService(SnowflakeIdGenerator idGenerator,
                             ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
                             ExamMapper examMapper,
                             ExamSessionMapper examSessionMapper,
                             AnswerMapper answerMapper,
                             SessionRiskEventMapper sessionRiskEventMapper,
                             SessionRiskSummaryMapper sessionRiskSummaryMapper,
                             AntiCheatRuleEngine antiCheatRuleEngine,
                             AntiCheatProperties antiCheatProperties,
                             StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper) {
        this.idGenerator = idGenerator;
        this.rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        this.examMapper = examMapper;
        this.examSessionMapper = examSessionMapper;
        this.answerMapper = answerMapper;
        this.sessionRiskEventMapper = sessionRiskEventMapper;
        this.sessionRiskSummaryMapper = sessionRiskSummaryMapper;
        this.antiCheatRuleEngine = antiCheatRuleEngine;
        this.antiCheatProperties = antiCheatProperties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Exam createExam(CreateExamRequest request, String userId) {
        protectDuplicateCreate(userId, request);

        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "startTime must be before endTime");
        }

        ExamEntity entity = new ExamEntity();
        entity.setId(idGenerator.nextId());
        entity.setPaperId(parseLong("paperId", request.getPaperId()));
        entity.setTitle(request.getTitle());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setAntiCheatLevel(request.getAntiCheatLevel());
        entity.setStatus(resolveExamStatus(entity.getStartTime(), entity.getEndTime(), LocalDateTime.now()).name());
        entity.setCreatedBy(parseLong("createdBy", userId));
        examMapper.insert(entity);

        Exam exam = toExam(entity);
        putExamCache(exam);
        return exam;
    }

    @Transactional
    public Map<String, Object> startExam(String examId, String userId, String ip) {
        Exam exam = getExam(examId);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime()) || now.isAfter(exam.getEndTime())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Exam is not in active window");
        }

        long examLongId = parseLong("examId", examId);
        long userLongId = parseLong("userId", userId);

        ExamSessionEntity activeSession = examSessionMapper.selectOne(
                Wrappers.lambdaQuery(ExamSessionEntity.class)
                        .eq(ExamSessionEntity::getExamId, examLongId)
                        .eq(ExamSessionEntity::getUserId, userLongId)
                        .eq(ExamSessionEntity::getStatus, SessionStatus.IN_PROGRESS.name())
                        .orderByDesc(ExamSessionEntity::getId)
                        .last("limit 1")
        );

        ExamSessionEntity sessionEntity;
        if (activeSession != null) {
            sessionEntity = activeSession;
        } else {
            sessionEntity = new ExamSessionEntity();
            sessionEntity.setId(idGenerator.nextId());
            sessionEntity.setExamId(examLongId);
            sessionEntity.setUserId(userLongId);
            sessionEntity.setStartTime(now);
            sessionEntity.setSubmitTime(null);
            sessionEntity.setStatus(SessionStatus.IN_PROGRESS.name());
            sessionEntity.setIpAtStart(ip);
            sessionEntity.setSwitchScreenCount(0);
            sessionEntity.setLastSaveTime(now);
            examSessionMapper.insert(sessionEntity);
        }

        long timeLimitSeconds = Math.max(1, Duration.between(now, exam.getEndTime()).toSeconds());
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", String.valueOf(sessionEntity.getId()));
        payload.put("serverTime", now);
        payload.put("timeLimitSeconds", timeLimitSeconds);
        return payload;
    }

    @Transactional
    public void saveAnswers(String sessionId, SaveAnswersRequest request, String userId) {
        long sessionLongId = parseLong("sessionId", sessionId);
        ExamSessionEntity session = getSessionEntity(sessionLongId);

        if (!String.valueOf(session.getUserId()).equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Session does not belong to current user");
        }
        if (!SessionStatus.IN_PROGRESS.name().equals(session.getStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Session is not editable");
        }

        for (AnswerItem item : request.getAnswers()) {
            long questionLongId = parseLong("questionId", item.getQuestionId());
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
                entity.setAnswerContent(writeAsJson(item.getAnswerContent()));
                entity.setIsMarkedForReview(Boolean.TRUE.equals(item.getMarkedForReview()) ? 1 : 0);
                answerMapper.insert(entity);
            } else {
                entity.setAnswerContent(writeAsJson(item.getAnswerContent()));
                entity.setIsMarkedForReview(Boolean.TRUE.equals(item.getMarkedForReview()) ? 1 : 0);
                entity.setUpdatedAt(LocalDateTime.now());
                answerMapper.updateById(entity);
            }
        }

        session.setLastSaveTime(LocalDateTime.now());
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

        session.setStatus(SessionStatus.SUBMITTED.name());
        session.setSubmitTime(LocalDateTime.now());
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
                "submittedAt", session.getSubmitTime()
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

    public Map<String, Object> getSessionRisk(String sessionId) {
        long sessionLongId = parseLong("sessionId", sessionId);
        ExamSessionEntity session = getSessionEntity(sessionLongId);
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

    public Map<String, Object> listExamRisks(String examId, String riskLevel, Long page, Long size) {
        long examLongId = parseLong("examId", examId);
        if (examMapper.selectById(examLongId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam not found");
        }
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

    private void publishSubmittedEvent(ExamSubmittedEvent event) {
        if (rabbitTemplate == null) {
            log.warn("RabbitTemplate unavailable, skip publish exam.submitted event: {}", event.getEventId());
            return;
        }
        try {
            rabbitTemplate.convertAndSend(
                    RabbitConfig.EXAM_EXCHANGE,
                    RabbitConfig.EXAM_SUBMITTED_ROUTING_KEY,
                    event,
                    new CorrelationData(event.getEventId())
            );
        } catch (Exception ex) {
            log.error("Publish exam.submitted failed: {}", ex.getMessage(), ex);
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
