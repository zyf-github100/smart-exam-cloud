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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GradingDomainService {

    private static final Logger log = LoggerFactory.getLogger(GradingDomainService.class);
    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final Duration EVENT_DEDUP_TTL = Duration.ofDays(7);
    private static final Duration MANUAL_DEDUP_TTL = Duration.ofSeconds(8);
    private static final String EVENT_DEDUP_PREFIX = "grading:event:exam-submitted:";
    private static final String MANUAL_DEDUP_PREFIX = "grading:manual:dedup:";
    private static final Set<String> OBJECTIVE_TYPES = Set.of("SINGLE", "MULTI", "JUDGE", "FILL");

    private final SnowflakeIdGenerator idGenerator;
    private final RabbitTemplate rabbitTemplate;
    private final GradingTaskMapper gradingTaskMapper;
    private final QuestionScoreMapper questionScoreMapper;
    private final ExamReadMapper examReadMapper;
    private final QuestionReadMapper questionReadMapper;
    private final ResultReleaseMapper resultReleaseMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public GradingDomainService(SnowflakeIdGenerator idGenerator,
                                RabbitTemplate rabbitTemplate,
                                GradingTaskMapper gradingTaskMapper,
                                QuestionScoreMapper questionScoreMapper,
                                ExamReadMapper examReadMapper,
                                QuestionReadMapper questionReadMapper,
                                ResultReleaseMapper resultReleaseMapper,
                                StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper) {
        this.idGenerator = idGenerator;
        this.rabbitTemplate = rabbitTemplate;
        this.gradingTaskMapper = gradingTaskMapper;
        this.questionScoreMapper = questionScoreMapper;
        this.examReadMapper = examReadMapper;
        this.questionReadMapper = questionReadMapper;
        this.resultReleaseMapper = resultReleaseMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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
    public GradingTask manualScore(String taskId, ManualScoreRequest request, String graderId, String role) {
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
        return toTask(taskEntity, scoreEntities);
    }

    @Transactional
    public Map<String, Object> updateExamResultRelease(String examId, boolean released, String operatorId, String role) {
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
                    ? isObjectiveType(question.getType())
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

            boolean objective = isObjectiveType(question.getType());
            BigDecimal maxScore = toScore(paperQuestion.getScore());
            QuestionScoreEntity scoreEntity = new QuestionScoreEntity();
            scoreEntity.setQuestionId(paperQuestion.getQuestionId());
            scoreEntity.setMaxScore(maxScore);
            scoreEntity.setIsObjective(objective ? 1 : 0);

            if (objective) {
                boolean correct = isObjectiveAnswerCorrect(question, answerMap.get(paperQuestion.getQuestionId()));
                BigDecimal gotScore = correct ? maxScore : ZERO_SCORE;
                scoreEntity.setGotScore(gotScore);
                scoreEntity.setComment(correct ? "AUTO_CORRECT" : "AUTO_WRONG");
                objectiveScore = objectiveScore.add(gotScore);
            } else {
                manualRequired = true;
                scoreEntity.setGotScore(ZERO_SCORE);
                scoreEntity.setComment("PENDING_MANUAL");
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

    private boolean isObjectiveType(String questionType) {
        return OBJECTIVE_TYPES.contains(normalizeToken(questionType));
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

    private boolean isObjectiveAnswerCorrect(QuestionSnapshot question, JsonNode answerNode) {
        String type = normalizeToken(question.getType());
        return switch (type) {
            case "SINGLE" -> isSingleCorrect(question.getAnswer(), answerNode);
            case "MULTI" -> isMultiCorrect(question.getAnswer(), answerNode);
            case "JUDGE" -> isJudgeCorrect(question.getAnswer(), answerNode);
            case "FILL" -> isFillCorrect(question.getAnswer(), answerNode);
            default -> false;
        };
    }

    private boolean isSingleCorrect(String expectedRaw, JsonNode answerNode) {
        String expected = normalizeToken(expectedRaw);
        String actual = normalizeToken(extractFirstScalar(answerNode));
        return StringUtils.hasText(expected) && expected.equals(actual);
    }

    private boolean isMultiCorrect(String expectedRaw, JsonNode answerNode) {
        Set<String> expectedSet = splitTokens(expectedRaw);
        Set<String> actualSet = readMultiAnswerTokens(answerNode);
        return !expectedSet.isEmpty() && expectedSet.equals(actualSet);
    }

    private boolean isJudgeCorrect(String expectedRaw, JsonNode answerNode) {
        Boolean expected = parseBooleanValue(expectedRaw);
        Boolean actual = parseBooleanValue(extractFirstScalar(answerNode));
        return expected != null && expected.equals(actual);
    }

    private boolean isFillCorrect(String expectedRaw, JsonNode answerNode) {
        String expected = normalizeFillAnswer(expectedRaw);
        String actual = normalizeFillAnswer(extractFirstScalar(answerNode));
        return StringUtils.hasText(expected) && expected.equalsIgnoreCase(actual);
    }

    private Set<String> readMultiAnswerTokens(JsonNode answerNode) {
        if (answerNode == null || answerNode.isNull()) {
            return Set.of();
        }
        if (answerNode.isArray()) {
            Set<String> tokens = new HashSet<>();
            for (JsonNode node : answerNode) {
                String token = normalizeToken(node == null ? null : node.asText());
                if (StringUtils.hasText(token)) {
                    tokens.add(token);
                }
            }
            return tokens;
        }
        return splitTokens(extractFirstScalar(answerNode));
    }

    private Set<String> splitTokens(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return Set.of();
        }
        String[] parts = rawValue.split("[,，\\s]+");
        Set<String> tokens = new HashSet<>();
        for (String part : parts) {
            String token = normalizeToken(part);
            if (StringUtils.hasText(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String extractFirstScalar(JsonNode answerNode) {
        if (answerNode == null || answerNode.isNull()) {
            return null;
        }
        if (answerNode.isArray()) {
            if (answerNode.size() == 0) {
                return null;
            }
            return answerNode.get(0).asText();
        }
        return answerNode.asText();
    }

    private Boolean parseBooleanValue(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "t", "yes", "y" -> true;
            case "false", "0", "f", "no", "n" -> false;
            default -> null;
        };
    }

    private String normalizeFillAnswer(String rawValue) {
        return rawValue == null ? "" : rawValue.trim();
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
            case "MULTI" -> splitTokensOrdered(rawAnswer);
            case "JUDGE" -> parseBooleanValue(rawAnswer);
            default -> rawAnswer.trim();
        };
    }

    private List<String> splitTokensOrdered(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }
        String[] parts = rawValue.split("[,\\uFF0C\\s]+");
        LinkedHashMap<String, String> dedup = new LinkedHashMap<>();
        for (String part : parts) {
            String token = normalizeToken(part);
            if (StringUtils.hasText(token) && !dedup.containsKey(token)) {
                dedup.put(token, token);
            }
        }
        return new ArrayList<>(dedup.values());
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
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXAM_EXCHANGE,
                RabbitConfig.SCORE_PUBLISHED_ROUTING_KEY,
                event,
                new CorrelationData(event.getEventId())
        );
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
