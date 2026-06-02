package com.smart.exam.grading.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.core.event.ExamSubmittedEvent;
import com.smart.exam.common.core.event.ScorePublishedEvent;
import com.smart.exam.common.core.id.SnowflakeIdGenerator;
import com.smart.exam.common.web.audit.AuditActions;
import com.smart.exam.common.web.audit.AuditLogCommand;
import com.smart.exam.common.web.audit.AuditLogService;
import com.smart.exam.common.web.audit.AuditModules;
import com.smart.exam.common.web.audit.AuditTargetTypes;
import com.smart.exam.grading.config.RabbitConfig;
import com.smart.exam.grading.dto.ManualScoreRequest;
import com.smart.exam.grading.entity.GradingTaskEntity;
import com.smart.exam.grading.entity.QuestionScoreEntity;
import com.smart.exam.grading.entity.ResultReleaseEntity;
import com.smart.exam.grading.mapper.ExamReadMapper;
import com.smart.exam.grading.mapper.GradingTaskMapper;
import com.smart.exam.grading.mapper.QuestionReadMapper;
import com.smart.exam.grading.mapper.QuestionScoreMapper;
import com.smart.exam.grading.mapper.ResultReleaseMapper;
import com.smart.exam.grading.model.GradingTask;
import com.smart.exam.grading.model.GradingTaskStatus;
import com.smart.exam.grading.model.QuestionScore;
import com.smart.exam.grading.rule.QuestionAnswerSupport;
import com.smart.exam.grading.rule.QuestionGradingResult;
import com.smart.exam.grading.rule.QuestionGradingRuleRegistry;
import com.smart.exam.grading.model.scoring.AnswerSnapshot;
import com.smart.exam.grading.model.scoring.ExamSnapshot;
import com.smart.exam.grading.model.scoring.PaperQuestionDetailSnapshot;
import com.smart.exam.grading.model.scoring.PaperQuestionSnapshot;
import com.smart.exam.grading.model.scoring.QuestionSnapshot;
import com.smart.exam.grading.model.scoring.SessionSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class GradingDomainService {

    private static final Logger log = LoggerFactory.getLogger(GradingDomainService.class);
    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final Duration EVENT_DEDUP_TTL = Duration.ofDays(7);
    private static final Duration MANUAL_DEDUP_TTL = Duration.ofSeconds(8);
    private static final Duration PUBLISH_CONFIRM_TIMEOUT = Duration.ofSeconds(5);
    private static final String EVENT_DEDUP_PREFIX = "grading:event:exam-submitted:";
    private static final String MANUAL_DEDUP_PREFIX = "grading:manual:dedup:";

    private final SnowflakeIdGenerator idGenerator;
    private final RabbitTemplate rabbitTemplate;
    private final GradingTaskMapper gradingTaskMapper;
    private final QuestionScoreMapper questionScoreMapper;
    private final ExamReadMapper examReadMapper;
    private final QuestionReadMapper questionReadMapper;
    private final ResultReleaseMapper resultReleaseMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final QuestionGradingRuleRegistry questionGradingRuleRegistry;

    public GradingDomainService(SnowflakeIdGenerator idGenerator,
                                RabbitTemplate rabbitTemplate,
                                GradingTaskMapper gradingTaskMapper,
                                QuestionScoreMapper questionScoreMapper,
                                ExamReadMapper examReadMapper,
                                QuestionReadMapper questionReadMapper,
                                ResultReleaseMapper resultReleaseMapper,
                                StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                AuditLogService auditLogService,
                                QuestionGradingRuleRegistry questionGradingRuleRegistry) {
        this.idGenerator = idGenerator;
        this.rabbitTemplate = rabbitTemplate;
        this.gradingTaskMapper = gradingTaskMapper;
        this.questionScoreMapper = questionScoreMapper;
        this.examReadMapper = examReadMapper;
        this.questionReadMapper = questionReadMapper;
        this.resultReleaseMapper = resultReleaseMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.questionGradingRuleRegistry = questionGradingRuleRegistry;
    }

    @Transactional
    public void onExamSubmitted(ExamSubmittedEvent event) {
        Long sessionId = parseLong("sessionId", event.getSessionId());
        GradingTaskEntity existingTask = gradingTaskMapper.selectOne(
                Wrappers.lambdaQuery(GradingTaskEntity.class)
                        .eq(GradingTaskEntity::getSessionId, sessionId)
                        .last("limit 1")
        );
        if (existingTask != null) {
            if (GradingTaskStatus.AUTO_DONE.name().equals(existingTask.getStatus())
                    || GradingTaskStatus.DONE.name().equals(existingTask.getStatus())) {
                log.warn("Existing finished task found, republish score, sessionId={}", event.getSessionId());
                publishScore(existingTask);
                return;
            } else {
                if (isTaskScoreComplete(existingTask)) {
                    log.info("Skip duplicate task by sessionId, sessionId={}", event.getSessionId());
                    return;
                }
                log.warn("Detected incomplete task, rebuild by sessionId, taskId={}, sessionId={}",
                        existingTask.getId(),
                        event.getSessionId());
                questionScoreMapper.delete(
                        Wrappers.lambdaQuery(QuestionScoreEntity.class)
                                .eq(QuestionScoreEntity::getTaskId, existingTask.getId())
                );
                gradingTaskMapper.deleteById(existingTask.getId());
            }
        }

        boolean eventDedupAcquired = acquireEventDedup(event.getEventId());
        if (!eventDedupAcquired) {
            log.info("Event dedup key already exists, fallback to DB consistency checks, eventId={}",
                    event.getEventId());
        }

        try {
            ObjectiveScoringResult scoringResult = scoreObjectiveQuestions(event);

            GradingTaskEntity taskEntity = new GradingTaskEntity();
            taskEntity.setId(idGenerator.nextId());
            taskEntity.setExamId(parseLong("examId", event.getExamId()));
            taskEntity.setSessionId(sessionId);
            taskEntity.setUserId(parseLong("userId", event.getUserId()));
            taskEntity.setCreatedAt(LocalDateTime.now());
            taskEntity.setUpdatedAt(LocalDateTime.now());
            taskEntity.setObjectiveScore(scoringResult.objectiveScore());
            taskEntity.setSubjectiveScore(ZERO_SCORE);
            taskEntity.setTotalScore(scoringResult.objectiveScore());
            taskEntity.setStatus(scoringResult.manualRequired()
                    ? GradingTaskStatus.MANUAL_REQUIRED.name()
                    : GradingTaskStatus.AUTO_DONE.name());
            gradingTaskMapper.insert(taskEntity);

            for (QuestionScoreEntity scoreEntity : scoringResult.questionScores()) {
                scoreEntity.setTaskId(taskEntity.getId());
                questionScoreMapper.insert(scoreEntity);
            }

            if (!scoringResult.manualRequired()) {
                publishScore(taskEntity, scoringResult.questionScores());
            }
        } catch (RuntimeException ex) {
            if (eventDedupAcquired) {
                releaseEventDedup(event.getEventId());
            }
            throw ex;
        }
    }

    public Collection<GradingTask> listTasks(String status, String operatorId, String role) {
        String normalizedStatus = normalizeTaskStatus(status);
        List<GradingTaskEntity> taskEntities;
        if (isAdminRole(role)) {
            if (!StringUtils.hasText(normalizedStatus)) {
                taskEntities = gradingTaskMapper.selectList(
                        Wrappers.lambdaQuery(GradingTaskEntity.class)
                                .orderByDesc(GradingTaskEntity::getCreatedAt)
                                .orderByDesc(GradingTaskEntity::getId)
                );
            } else {
                taskEntities = gradingTaskMapper.selectList(
                        Wrappers.lambdaQuery(GradingTaskEntity.class)
                                .eq(GradingTaskEntity::getStatus, normalizedStatus)
                                .orderByDesc(GradingTaskEntity::getCreatedAt)
                                .orderByDesc(GradingTaskEntity::getId)
                );
            }
        } else {
            Long teacherId = parseLong("teacherId", operatorId);
            taskEntities = gradingTaskMapper.selectTeacherTasks(teacherId, normalizedStatus);
        }

        if (taskEntities.isEmpty()) {
            return List.of();
        }

        List<Long> taskIds = taskEntities.stream().map(GradingTaskEntity::getId).toList();
        Map<Long, List<QuestionScoreEntity>> scoreMap = questionScoreMapper.selectList(
                Wrappers.lambdaQuery(QuestionScoreEntity.class)
                        .in(QuestionScoreEntity::getTaskId, taskIds)
                        .orderByAsc(QuestionScoreEntity::getId)
        ).stream().collect(Collectors.groupingBy(QuestionScoreEntity::getTaskId));

        return taskEntities.stream()
                .map(entity -> toTask(entity, scoreMap.getOrDefault(entity.getId(), List.of())))
                .toList();
    }

    @Transactional
    public GradingTask manualScore(String taskId,
                                   ManualScoreRequest request,
                                   String graderId,
                                   String role,
                                   String ip,
                                   String userAgent) {
        protectDuplicateManualScore(taskId, graderId, request);

        Long taskLongId = parseLong("taskId", taskId);
        GradingTaskEntity taskEntity = gradingTaskMapper.selectById(taskLongId);
        if (taskEntity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Grading task not found");
        }
        ensureTaskAccessible(taskEntity, graderId, role);
        if (!GradingTaskStatus.MANUAL_REQUIRED.name().equals(taskEntity.getStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Task is not in manual scoring state");
        }

        List<QuestionScoreEntity> oldSubjectiveScores = questionScoreMapper.selectList(
                Wrappers.lambdaQuery(QuestionScoreEntity.class)
                        .eq(QuestionScoreEntity::getTaskId, taskLongId)
                        .eq(QuestionScoreEntity::getIsObjective, 0)
                        .orderByAsc(QuestionScoreEntity::getId)
        );
        if (oldSubjectiveScores.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Task does not contain subjective questions");
        }

        Map<Long, BigDecimal> maxScoreByQuestion = oldSubjectiveScores.stream()
                .collect(Collectors.toMap(
                        QuestionScoreEntity::getQuestionId,
                        score -> safeScore(score.getMaxScore()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, ManualScoreRequest.ManualScoreItem> requestScores = parseManualScores(request);
        validateManualScoreCoverage(maxScoreByQuestion.keySet(), requestScores.keySet());

        questionScoreMapper.delete(
                Wrappers.lambdaQuery(QuestionScoreEntity.class)
                        .eq(QuestionScoreEntity::getTaskId, taskLongId)
                        .eq(QuestionScoreEntity::getIsObjective, 0)
        );

        BigDecimal subjectiveScore = ZERO_SCORE;
        for (Map.Entry<Long, ManualScoreRequest.ManualScoreItem> entry : requestScores.entrySet()) {
            Long questionId = entry.getKey();
            ManualScoreRequest.ManualScoreItem item = entry.getValue();
            BigDecimal maxScore = maxScoreByQuestion.get(questionId);
            BigDecimal gotScore = toScore(item.getGotScore());
            if (gotScore.compareTo(ZERO_SCORE) < 0 || gotScore.compareTo(maxScore) > 0) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Manual score out of range for questionId=" + questionId);
            }

            QuestionScoreEntity scoreEntity = new QuestionScoreEntity();
            scoreEntity.setTaskId(taskLongId);
            scoreEntity.setQuestionId(questionId);
            scoreEntity.setMaxScore(maxScore);
            scoreEntity.setGotScore(gotScore);
            scoreEntity.setComment(item.getComment());
            scoreEntity.setIsObjective(0);
            questionScoreMapper.insert(scoreEntity);
            subjectiveScore = subjectiveScore.add(gotScore);
        }

        taskEntity.setSubjectiveScore(subjectiveScore);
        taskEntity.setTotalScore(safeScore(taskEntity.getObjectiveScore()).add(subjectiveScore));
        taskEntity.setStatus(GradingTaskStatus.DONE.name());
        taskEntity.setGraderId(parseLong("graderId", graderId));
        taskEntity.setUpdatedAt(LocalDateTime.now());
        gradingTaskMapper.updateById(taskEntity);

        List<QuestionScoreEntity> scoreEntities = loadTaskQuestionScores(taskLongId);
        publishScore(taskEntity, scoreEntities);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("examId", String.valueOf(taskEntity.getExamId()));
        detail.put("sessionId", String.valueOf(taskEntity.getSessionId()));
        detail.put("studentId", String.valueOf(taskEntity.getUserId()));
        detail.put("manualQuestionCount", requestScores.size());
        detail.put("subjectiveScore", subjectiveScore.doubleValue());
        detail.put("totalScore", safeScore(taskEntity.getTotalScore()).doubleValue());
        detail.put("scores", requestScores.entrySet().stream().map(entry -> {
            Map<String, Object> itemPayload = new LinkedHashMap<>();
            itemPayload.put("questionId", String.valueOf(entry.getKey()));
            itemPayload.put("gotScore", entry.getValue().getGotScore());
            itemPayload.put("comment", entry.getValue().getComment());
            return itemPayload;
        }).toList());
        auditLogService.record(
                AuditLogCommand.builder()
                        .moduleKey(AuditModules.GRADING)
                        .operatorId(graderId)
                        .operatorRole(role)
                        .action(AuditActions.GRADING_MANUAL_SCORED)
                        .targetType(AuditTargetTypes.GRADING_TASK)
                        .targetId(taskId)
                        .detail(detail)
                        .ip(ip)
                        .userAgent(userAgent)
                        .build()
        );
        return toTask(taskEntity, scoreEntities);
    }

    @Transactional
    public Map<String, Object> updateExamResultRelease(String examId,
                                                       boolean released,
                                                       String operatorId,
                                                       String role,
                                                       String ip,
                                                       String userAgent) {
        long examLongId = parseLong("examId", examId);
        ExamSnapshot examSnapshot = examReadMapper.selectExamById(examLongId);
        if (examSnapshot == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam not found");
        }

        ensureExamAccessible(examLongId, operatorId, role);

        ResultReleaseEntity entity = resultReleaseMapper.selectById(examLongId);
        boolean existed = entity != null;
        if (entity == null) {
            entity = new ResultReleaseEntity();
            entity.setExamId(examLongId);
        }

        LocalDateTime now = LocalDateTime.now();
        entity.setReleased(released ? 1 : 0);
        entity.setReleasedBy(parseLong("operatorId", operatorId));
        entity.setReleasedAt(released ? now : null);
        entity.setUpdatedAt(now);

        if (existed) {
            resultReleaseMapper.updateById(entity);
        } else {
            resultReleaseMapper.insert(entity);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("examId", examId);
        payload.put("released", released);
        payload.put("releasedAt", entity.getReleasedAt());
        payload.put("releasedBy", entity.getReleasedBy() == null ? null : String.valueOf(entity.getReleasedBy()));
        payload.put("effective", released || hasExamEnded(examSnapshot));
        auditLogService.record(
                AuditLogCommand.builder()
                        .moduleKey(AuditModules.GRADING)
                        .operatorId(operatorId)
                        .operatorRole(role)
                        .action(AuditActions.EXAM_RESULT_RELEASE_UPDATED)
                        .targetType(AuditTargetTypes.EXAM)
                        .targetId(examId)
                        .detail(payload)
                        .ip(ip)
                        .userAgent(userAgent)
                        .build()
        );
        return payload;
    }

    public Map<String, Object> getExamResultRelease(String examId, String operatorId, String role) {
        long examLongId = parseLong("examId", examId);
        ExamSnapshot examSnapshot = examReadMapper.selectExamById(examLongId);
        if (examSnapshot == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam not found");
        }

        ensureExamAccessible(examLongId, operatorId, role);
        ResultReleaseEntity entity = resultReleaseMapper.selectById(examLongId);

        boolean released = entity != null && entity.getReleased() != null && entity.getReleased() == 1;
        Map<String, Object> payload = new HashMap<>();
        payload.put("examId", examId);
        payload.put("released", released);
        payload.put("releasedAt", entity == null ? null : entity.getReleasedAt());
        payload.put("releasedBy", entity == null || entity.getReleasedBy() == null
                ? null
                : String.valueOf(entity.getReleasedBy()));
        payload.put("effective", released || hasExamEnded(examSnapshot));
        return payload;
    }

    public Map<String, Object> getStudentSessionResult(String sessionId, String operatorId, String role) {
        long sessionLongId = parseLong("sessionId", sessionId);
        SessionSnapshot session = examReadMapper.selectSessionById(sessionLongId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam session not found");
        }

        if (!isAdminRole(role)) {
            long studentId = parseLong("studentId", operatorId);
            if (session.getUserId() == null || !session.getUserId().equals(studentId)) {
                throw new BizException(ErrorCode.FORBIDDEN, "Session does not belong to current student");
            }
        }

        String normalizedSessionStatus = normalizeToken(session.getStatus());
        if (!"SUBMITTED".equals(normalizedSessionStatus) && !"FORCE_SUBMITTED".equals(normalizedSessionStatus)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Exam session has not been submitted");
        }

        ExamSnapshot examSnapshot = examReadMapper.selectExamById(session.getExamId());
        if (examSnapshot == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam not found");
        }

        GradingTaskEntity taskEntity = gradingTaskMapper.selectOne(
                Wrappers.lambdaQuery(GradingTaskEntity.class)
                        .eq(GradingTaskEntity::getSessionId, sessionLongId)
                        .last("limit 1")
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("examId", String.valueOf(session.getExamId()));
        payload.put("sessionStatus", session.getStatus());
        payload.put("submittedAt", session.getSubmitTime());
        boolean detailReleased = isResultDetailReleasedForStudent(examSnapshot);
        payload.put("detailReleased", detailReleased);
        payload.put("detailMessage", detailReleased ? null : "标准答案与解析将在考试结束后或老师发布后开放");

        if (taskEntity == null || !isTaskReadyForStudent(taskEntity.getStatus())) {
            payload.put("ready", false);
            payload.put("taskStatus", taskEntity == null ? "PENDING" : taskEntity.getStatus());
            payload.put("message", "成绩正在评阅，主观题完成评分后可查看完整成绩与解析");

            Map<String, Object> summary = new HashMap<>();
            summary.put("objectiveScore", taskEntity == null ? null : nullableDouble(taskEntity.getObjectiveScore()));
            summary.put("subjectiveScore", null);
            summary.put("totalScore", null);
            summary.put("publishedAt", taskEntity == null ? null : taskEntity.getUpdatedAt());
            payload.put("summary", summary);
            payload.put("questions", List.of());
            return payload;
        }

        List<QuestionScoreEntity> scoreEntities = loadTaskQuestionScores(taskEntity.getId());
        Map<Long, QuestionScoreEntity> scoreByQuestionId = new HashMap<>();
        for (QuestionScoreEntity scoreEntity : scoreEntities) {
            if (scoreEntity == null || scoreEntity.getQuestionId() == null) {
                continue;
            }
            scoreByQuestionId.put(scoreEntity.getQuestionId(), scoreEntity);
        }

        Map<Long, Object> answerByQuestionId = readAnswerContentByQuestionId(sessionLongId);
        List<PaperQuestionDetailSnapshot> questionDetails =
                questionReadMapper.selectPaperQuestionDetailsByPaperId(examSnapshot.getPaperId());

        List<Map<String, Object>> questionPayload = new ArrayList<>(questionDetails.size());
        for (PaperQuestionDetailSnapshot question : questionDetails) {
            if (question == null || question.getQuestionId() == null) {
                continue;
            }
            QuestionScoreEntity scoreEntity = scoreByQuestionId.get(question.getQuestionId());
            BigDecimal maxScore = scoreEntity == null ? toScore(question.getScore()) : safeScore(scoreEntity.getMaxScore());
            BigDecimal gotScore = scoreEntity == null ? ZERO_SCORE : safeScore(scoreEntity.getGotScore());
            boolean objective = scoreEntity == null
                    ? questionGradingRuleRegistry.supports(question.getType())
                    : (scoreEntity.getIsObjective() != null && scoreEntity.getIsObjective() == 1);

            Map<String, Object> questionItem = new HashMap<>();
            questionItem.put("questionId", String.valueOf(question.getQuestionId()));
            questionItem.put("orderNo", question.getOrderNo());
            questionItem.put("type", question.getType());
            questionItem.put("stem", question.getStem());
            questionItem.put("analysis", detailReleased ? question.getAnalysis() : null);
            questionItem.put("options", parseQuestionOptions(question.getOptionsJson()));
            questionItem.put("standardAnswer", detailReleased
                    ? parseStandardAnswerForView(question.getType(), question.getAnswer())
                    : null);
            questionItem.put("myAnswer", answerByQuestionId.get(question.getQuestionId()));
            questionItem.put("maxScore", nullableDouble(maxScore));
            questionItem.put("gotScore", nullableDouble(gotScore));
            questionItem.put("objective", objective);
            questionItem.put("correct", objective && maxScore.compareTo(gotScore) == 0);
            questionPayload.add(questionItem);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("objectiveScore", nullableDouble(taskEntity.getObjectiveScore()));
        summary.put("subjectiveScore", nullableDouble(taskEntity.getSubjectiveScore()));
        summary.put("totalScore", nullableDouble(taskEntity.getTotalScore()));
        summary.put("publishedAt", taskEntity.getUpdatedAt());

        payload.put("ready", true);
        payload.put("taskStatus", taskEntity.getStatus());
        payload.put("summary", summary);
        payload.put("questions", questionPayload);
        return payload;
    }

    private ObjectiveScoringResult scoreObjectiveQuestions(ExamSubmittedEvent event) {
        long examId = parseLong("examId", event.getExamId());
        long sessionId = parseLong("sessionId", event.getSessionId());

        ExamSnapshot examSnapshot = examReadMapper.selectExamById(examId);
        if (examSnapshot == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam not found: " + event.getExamId());
        }

        List<PaperQuestionSnapshot> paperQuestions = questionReadMapper.selectPaperQuestionsByPaperId(examSnapshot.getPaperId());
        if (paperQuestions.isEmpty()) {
            return new ObjectiveScoringResult(ZERO_SCORE, false, List.of());
        }

        Set<Long> questionIds = paperQuestions.stream()
                .map(PaperQuestionSnapshot::getQuestionId)
                .collect(Collectors.toSet());
        List<QuestionSnapshot> questionSnapshots = questionReadMapper.selectQuestionsByIds(questionIds);
        Map<Long, QuestionSnapshot> questionMap = questionSnapshots.stream()
                .collect(Collectors.toMap(QuestionSnapshot::getId, question -> question));
        if (questionMap.size() != questionIds.size()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Paper contains unknown question definitions");
        }

        Map<Long, JsonNode> answerMap = loadAnswersBySession(sessionId);
        BigDecimal objectiveScore = ZERO_SCORE;
        boolean manualRequired = false;
        List<QuestionScoreEntity> scoreEntities = new ArrayList<>();

        for (PaperQuestionSnapshot paperQuestion : paperQuestions) {
            QuestionSnapshot question = questionMap.get(paperQuestion.getQuestionId());
            if (question == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Question snapshot missing");
            }

            BigDecimal maxScore = toScore(paperQuestion.getScore());
            QuestionGradingResult gradingResult = questionGradingRuleRegistry.grade(
                    question,
                    answerMap.get(paperQuestion.getQuestionId()),
                    maxScore
            );
            QuestionScoreEntity scoreEntity = new QuestionScoreEntity();
            scoreEntity.setQuestionId(paperQuestion.getQuestionId());
            scoreEntity.setMaxScore(maxScore);
            scoreEntity.setIsObjective(gradingResult.objective() ? 1 : 0);
            scoreEntity.setGotScore(safeScore(gradingResult.gotScore()));
            scoreEntity.setComment(gradingResult.comment());

            if (gradingResult.manualRequired()) {
                manualRequired = true;
            } else if (gradingResult.objective()) {
                objectiveScore = objectiveScore.add(safeScore(gradingResult.gotScore()));
            }
            scoreEntities.add(scoreEntity);
        }

        return new ObjectiveScoringResult(objectiveScore, manualRequired, scoreEntities);
    }

    private Map<Long, JsonNode> loadAnswersBySession(long sessionId) {
        List<AnswerSnapshot> answers = examReadMapper.selectAnswersBySessionId(sessionId);
        Map<Long, JsonNode> answerMap = new HashMap<>();
        for (AnswerSnapshot answer : answers) {
            if (answer.getQuestionId() == null) {
                continue;
            }
            answerMap.put(answer.getQuestionId(), parseAnswerContent(answer.getAnswerContent()));
        }
        return answerMap;
    }

    private JsonNode parseAnswerContent(String rawAnswerContent) {
        if (!StringUtils.hasText(rawAnswerContent)) {
            return null;
        }
        try {
            return objectMapper.readTree(rawAnswerContent);
        } catch (Exception ex) {
            log.warn("Failed to parse answer content, fallback to text compare");
            return TextNode.valueOf(rawAnswerContent);
        }
    }

    private boolean isTaskScoreComplete(GradingTaskEntity taskEntity) {
        if (taskEntity == null || taskEntity.getId() == null || taskEntity.getExamId() == null) {
            return false;
        }
        ExamSnapshot examSnapshot = examReadMapper.selectExamById(taskEntity.getExamId());
        if (examSnapshot == null || examSnapshot.getPaperId() == null) {
            return false;
        }
        long expectedCount = questionReadMapper
                .selectPaperQuestionsByPaperId(examSnapshot.getPaperId())
                .size();
        Long actualCount = questionScoreMapper.selectCount(
                Wrappers.lambdaQuery(QuestionScoreEntity.class)
                        .eq(QuestionScoreEntity::getTaskId, taskEntity.getId())
        );
        return (actualCount == null ? 0L : actualCount) == expectedCount;
    }

    private String normalizeToken(String rawValue) {
        return rawValue == null ? "" : rawValue.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isTaskReadyForStudent(String taskStatus) {
        return GradingTaskStatus.AUTO_DONE.name().equals(taskStatus)
                || GradingTaskStatus.DONE.name().equals(taskStatus);
    }

    private boolean isResultDetailReleasedForStudent(ExamSnapshot examSnapshot) {
        if (examSnapshot == null || examSnapshot.getId() == null) {
            return false;
        }
        return hasExamEnded(examSnapshot) || isTeacherReleased(examSnapshot.getId());
    }

    private boolean hasExamEnded(ExamSnapshot examSnapshot) {
        if (examSnapshot == null || examSnapshot.getEndTime() == null) {
            return false;
        }
        return !LocalDateTime.now().isBefore(examSnapshot.getEndTime());
    }

    private boolean isTeacherReleased(Long examId) {
        if (examId == null) {
            return false;
        }
        ResultReleaseEntity entity = resultReleaseMapper.selectById(examId);
        return entity != null && entity.getReleased() != null && entity.getReleased() == 1;
    }

    private Map<Long, Object> readAnswerContentByQuestionId(long sessionId) {
        List<AnswerSnapshot> answers = examReadMapper.selectAnswersBySessionId(sessionId);
        if (answers == null || answers.isEmpty()) {
            return Map.of();
        }
        Map<Long, Object> result = new HashMap<>();
        for (AnswerSnapshot answer : answers) {
            if (answer == null || answer.getQuestionId() == null) {
                continue;
            }
            result.put(answer.getQuestionId(), parseAnswerPayload(answer.getAnswerContent()));
        }
        return result;
    }

    private Object parseAnswerPayload(String rawAnswerContent) {
        if (!StringUtils.hasText(rawAnswerContent)) {
            return null;
        }
        try {
            return objectMapper.readValue(rawAnswerContent, Object.class);
        } catch (Exception ex) {
            log.warn("Failed to parse answer payload, fallback raw text");
            return rawAnswerContent;
        }
    }

    private List<Map<String, Object>> parseQuestionOptions(String rawOptionsJson) {
        if (!StringUtils.hasText(rawOptionsJson)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> options = objectMapper.readValue(
                    rawOptionsJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );
            return options == null ? List.of() : options;
        } catch (Exception ex) {
            log.warn("Failed to parse question options");
            return List.of();
        }
    }

    private Object parseStandardAnswerForView(String questionType, String rawAnswer) {
        String type = normalizeToken(questionType);
        if (!StringUtils.hasText(rawAnswer)) {
            return "";
        }
        return switch (type) {
            case "MULTI" -> QuestionAnswerSupport.splitTokensOrdered(rawAnswer);
            case "JUDGE" -> QuestionAnswerSupport.parseBooleanValue(rawAnswer);
            default -> rawAnswer.trim();
        };
    }

    private void publishScore(GradingTaskEntity taskEntity) {
        publishScore(taskEntity, loadTaskQuestionScores(taskEntity.getId()));
    }

    private void publishScore(GradingTaskEntity taskEntity, List<QuestionScoreEntity> questionScores) {
        ScorePublishedEvent event = new ScorePublishedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setExamId(String.valueOf(taskEntity.getExamId()));
        event.setSessionId(String.valueOf(taskEntity.getSessionId()));
        event.setUserId(String.valueOf(taskEntity.getUserId()));
        event.setTotalScore(safeScore(taskEntity.getTotalScore()).doubleValue());
        event.setPublishedAt(OffsetDateTime.now());
        event.setQuestionScores(questionScores.stream()
                .map(this::toScorePayload)
                .toList());
        publishWithConfirm(event);
    }

    private void publishWithConfirm(ScorePublishedEvent event) {
        CorrelationData correlationData = new CorrelationData(event.getEventId());
        try {
            rabbitTemplate.convertAndSend(
                    RabbitConfig.EXAM_EXCHANGE,
                    RabbitConfig.SCORE_PUBLISHED_ROUTING_KEY,
                    event,
                    correlationData
            );
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(PUBLISH_CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (confirm == null || !confirm.isAck()) {
                String reason = confirm == null ? "confirm missing" : confirm.getReason();
                throw new BizException(ErrorCode.INTERNAL_ERROR, "Rabbit publish nacked: score published event, " + reason);
            }
            if (correlationData.getReturned() != null) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "Rabbit publish returned: score published event");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Interrupted while publishing score event");
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to publish score event");
        }
    }

    private ScorePublishedEvent.QuestionScorePayload toScorePayload(QuestionScoreEntity scoreEntity) {
        ScorePublishedEvent.QuestionScorePayload payload = new ScorePublishedEvent.QuestionScorePayload();
        payload.setQuestionId(String.valueOf(scoreEntity.getQuestionId()));
        payload.setMaxScore(safeScore(scoreEntity.getMaxScore()).doubleValue());
        payload.setGotScore(safeScore(scoreEntity.getGotScore()).doubleValue());
        payload.setObjective(scoreEntity.getIsObjective() != null && scoreEntity.getIsObjective() == 1);
        return payload;
    }

    private List<QuestionScoreEntity> loadTaskQuestionScores(Long taskId) {
        return questionScoreMapper.selectList(
                Wrappers.lambdaQuery(QuestionScoreEntity.class)
                        .eq(QuestionScoreEntity::getTaskId, taskId)
                        .orderByAsc(QuestionScoreEntity::getId)
        );
    }

    private GradingTask toTask(GradingTaskEntity taskEntity, List<QuestionScoreEntity> scoreEntities) {
        GradingTask task = new GradingTask();
        task.setId(String.valueOf(taskEntity.getId()));
        task.setExamId(String.valueOf(taskEntity.getExamId()));
        task.setSessionId(String.valueOf(taskEntity.getSessionId()));
        task.setUserId(String.valueOf(taskEntity.getUserId()));
        task.setStatus(GradingTaskStatus.valueOf(taskEntity.getStatus()));
        task.setObjectiveScore(nullableDouble(taskEntity.getObjectiveScore()));
        task.setSubjectiveScore(nullableDouble(taskEntity.getSubjectiveScore()));
        task.setTotalScore(nullableDouble(taskEntity.getTotalScore()));
        task.setGraderId(taskEntity.getGraderId() == null ? null : String.valueOf(taskEntity.getGraderId()));
        task.setCreatedAt(taskEntity.getCreatedAt());
        task.setUpdatedAt(taskEntity.getUpdatedAt());

        List<QuestionScore> questionScores = new ArrayList<>();
        for (QuestionScoreEntity scoreEntity : scoreEntities) {
            QuestionScore score = new QuestionScore();
            score.setQuestionId(String.valueOf(scoreEntity.getQuestionId()));
            score.setMaxScore(nullableDouble(scoreEntity.getMaxScore()));
            score.setGotScore(nullableDouble(scoreEntity.getGotScore()));
            score.setComment(scoreEntity.getComment());
            score.setObjective(scoreEntity.getIsObjective() != null && scoreEntity.getIsObjective() == 1);
            questionScores.add(score);
        }
        task.setQuestionScores(questionScores);
        return task;
    }

    private Double nullableDouble(BigDecimal decimal) {
        return decimal == null ? null : decimal.doubleValue();
    }

    private Map<Long, ManualScoreRequest.ManualScoreItem> parseManualScores(ManualScoreRequest request) {
        if (request == null || request.getScores() == null || request.getScores().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Manual score details cannot be empty");
        }
        Map<Long, ManualScoreRequest.ManualScoreItem> result = new LinkedHashMap<>();
        for (ManualScoreRequest.ManualScoreItem item : request.getScores()) {
            Long questionId = parseLong("questionId", item.getQuestionId());
            if (result.containsKey(questionId)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Duplicate questionId in manual scores: " + questionId);
            }
            result.put(questionId, item);
        }
        return result;
    }

    private void validateManualScoreCoverage(Set<Long> expectedQuestionIds, Set<Long> submittedQuestionIds) {
        if (expectedQuestionIds.size() != submittedQuestionIds.size()
                || !expectedQuestionIds.containsAll(submittedQuestionIds)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Manual scores must cover all subjective questions");
        }
    }

    private String normalizeTaskStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return GradingTaskStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid task status: " + status);
        }
    }

    private boolean isAdminRole(String role) {
        return "ADMIN".equalsIgnoreCase(String.valueOf(role).trim());
    }

    private void ensureExamAccessible(long examId, String operatorId, String role) {
        if (isAdminRole(role)) {
            return;
        }
        Long examOwnerId = examReadMapper.selectExamOwnerById(examId);
        if (examOwnerId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam not found");
        }
        long teacherId = parseLong("operatorId", operatorId);
        if (!examOwnerId.equals(teacherId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Exam does not belong to current teacher");
        }
    }

    private void ensureTaskAccessible(GradingTaskEntity taskEntity, String graderId, String role) {
        if (isAdminRole(role)) {
            return;
        }
        Long examOwnerId = examReadMapper.selectExamOwnerById(taskEntity.getExamId());
        if (examOwnerId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Exam not found");
        }
        long teacherId = parseLong("graderId", graderId);
        if (!examOwnerId.equals(teacherId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Grading task does not belong to current teacher");
        }
    }

    private BigDecimal toScore(Number rawScore) {
        return rawScore == null
                ? ZERO_SCORE
                : BigDecimal.valueOf(rawScore.doubleValue()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safeScore(BigDecimal score) {
        return score == null ? ZERO_SCORE : score.setScale(2, RoundingMode.HALF_UP);
    }

    private void protectDuplicateManualScore(String taskId, String graderId, ManualScoreRequest request) {
        String dedupKey = MANUAL_DEDUP_PREFIX + taskId + ":" + graderId + ":" + sha256(writeAsJson(request));
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", MANUAL_DEDUP_TTL);
            if (Boolean.FALSE.equals(success)) {
                throw new BizException(ErrorCode.CONFLICT, "Duplicate manual score request");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Manual score dedup unavailable", ex);
        }
    }

    private boolean acquireEventDedup(String eventId) {
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(
                    EVENT_DEDUP_PREFIX + eventId,
                    "1",
                    EVENT_DEDUP_TTL
            );
            return Boolean.TRUE.equals(success);
        } catch (Exception ex) {
            log.warn("Event dedup unavailable, fallback to DB uniqueness check", ex);
            return true;
        }
    }

    private void releaseEventDedup(String eventId) {
        try {
            redisTemplate.delete(EVENT_DEDUP_PREFIX + eventId);
        } catch (Exception ex) {
            log.warn("Event dedup release failed, eventId={}", eventId, ex);
        }
    }

    private String writeAsJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
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

    private record ObjectiveScoringResult(BigDecimal objectiveScore,
                                          boolean manualRequired,
                                          List<QuestionScoreEntity> questionScores) {
    }
}
