package com.smart.exam.grading.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.core.event.ExamSubmittedEvent;
import com.smart.exam.common.core.event.ScorePublishedEvent;
import com.smart.exam.common.core.id.SnowflakeIdGenerator;
import com.smart.exam.grading.config.RabbitConfig;
import com.smart.exam.grading.dto.ManualScoreRequest;
import com.smart.exam.grading.entity.GradingTaskEntity;
import com.smart.exam.grading.entity.QuestionScoreEntity;
import com.smart.exam.grading.mapper.GradingTaskMapper;
import com.smart.exam.grading.mapper.QuestionScoreMapper;
import com.smart.exam.grading.model.GradingTask;
import com.smart.exam.grading.model.GradingTaskStatus;
import com.smart.exam.grading.model.QuestionScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GradingDomainService {

    private static final Logger log = LoggerFactory.getLogger(GradingDomainService.class);
    private static final Duration EVENT_DEDUP_TTL = Duration.ofDays(7);
    private static final Duration MANUAL_DEDUP_TTL = Duration.ofSeconds(8);
    private static final String EVENT_DEDUP_PREFIX = "grading:event:exam-submitted:";
    private static final String MANUAL_DEDUP_PREFIX = "grading:manual:dedup:";

    private final SnowflakeIdGenerator idGenerator;
    private final RabbitTemplate rabbitTemplate;
    private final GradingTaskMapper gradingTaskMapper;
    private final QuestionScoreMapper questionScoreMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public GradingDomainService(SnowflakeIdGenerator idGenerator,
                                RabbitTemplate rabbitTemplate,
                                GradingTaskMapper gradingTaskMapper,
                                QuestionScoreMapper questionScoreMapper,
                                StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper) {
        this.idGenerator = idGenerator;
        this.rabbitTemplate = rabbitTemplate;
        this.gradingTaskMapper = gradingTaskMapper;
        this.questionScoreMapper = questionScoreMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void onExamSubmitted(ExamSubmittedEvent event) {
        if (!acquireEventDedup(event.getEventId())) {
            log.info("Skip duplicate exam submitted event: {}", event.getEventId());
            return;
        }

        Long sessionId = parseLong("sessionId", event.getSessionId());
        GradingTaskEntity existingTask = gradingTaskMapper.selectOne(
                Wrappers.lambdaQuery(GradingTaskEntity.class)
                        .eq(GradingTaskEntity::getSessionId, sessionId)
                        .last("limit 1")
        );
        if (existingTask != null) {
            log.info("Skip duplicate task by sessionId, sessionId={}", event.getSessionId());
            return;
        }

        GradingTaskEntity taskEntity = new GradingTaskEntity();
        taskEntity.setId(idGenerator.nextId());
        taskEntity.setExamId(parseLong("examId", event.getExamId()));
        taskEntity.setSessionId(sessionId);
        taskEntity.setUserId(parseLong("userId", event.getUserId()));
        taskEntity.setCreatedAt(LocalDateTime.now());
        taskEntity.setUpdatedAt(LocalDateTime.now());

        BigDecimal objectiveScore = calculateObjectiveScore(event.getSessionId());
        taskEntity.setObjectiveScore(objectiveScore);

        boolean manualRequired = Math.abs(event.getSessionId().hashCode()) % 2 == 0;
        if (manualRequired) {
            taskEntity.setStatus(GradingTaskStatus.MANUAL_REQUIRED.name());
            taskEntity.setSubjectiveScore(BigDecimal.ZERO);
            taskEntity.setTotalScore(objectiveScore);
        } else {
            taskEntity.setStatus(GradingTaskStatus.AUTO_DONE.name());
            taskEntity.setSubjectiveScore(BigDecimal.ZERO);
            taskEntity.setTotalScore(objectiveScore);
        }

        gradingTaskMapper.insert(taskEntity);

        if (!manualRequired) {
            publishScore(taskEntity);
        }
    }

    public Collection<GradingTask> listTasks(String status) {
        List<GradingTaskEntity> taskEntities;
        if (!StringUtils.hasText(status)) {
            taskEntities = gradingTaskMapper.selectList(
                    Wrappers.lambdaQuery(GradingTaskEntity.class)
                            .orderByDesc(GradingTaskEntity::getCreatedAt)
                            .orderByDesc(GradingTaskEntity::getId)
            );
        } else {
            GradingTaskStatus taskStatus;
            try {
                taskStatus = GradingTaskStatus.valueOf(status);
            } catch (IllegalArgumentException ex) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Invalid task status: " + status);
            }
            taskEntities = gradingTaskMapper.selectList(
                    Wrappers.lambdaQuery(GradingTaskEntity.class)
                            .eq(GradingTaskEntity::getStatus, taskStatus.name())
                            .orderByDesc(GradingTaskEntity::getCreatedAt)
                            .orderByDesc(GradingTaskEntity::getId)
            );
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
    public GradingTask manualScore(String taskId, ManualScoreRequest request, String graderId) {
        protectDuplicateManualScore(taskId, graderId, request);

        Long taskLongId = parseLong("taskId", taskId);
        GradingTaskEntity taskEntity = gradingTaskMapper.selectById(taskLongId);
        if (taskEntity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Grading task not found");
        }
        if (!GradingTaskStatus.MANUAL_REQUIRED.name().equals(taskEntity.getStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Task is not in manual scoring state");
        }

        questionScoreMapper.delete(
                Wrappers.lambdaQuery(QuestionScoreEntity.class)
                        .eq(QuestionScoreEntity::getTaskId, taskLongId)
                        .eq(QuestionScoreEntity::getIsObjective, 0)
        );

        BigDecimal subjectiveScore = BigDecimal.ZERO;
        for (ManualScoreRequest.ManualScoreItem item : request.getScores()) {
            BigDecimal got = BigDecimal.valueOf(item.getGotScore()).setScale(2, RoundingMode.HALF_UP);
            QuestionScoreEntity scoreEntity = new QuestionScoreEntity();
            scoreEntity.setTaskId(taskLongId);
            scoreEntity.setQuestionId(parseLong("questionId", item.getQuestionId()));
            scoreEntity.setMaxScore(got);
            scoreEntity.setGotScore(got);
            scoreEntity.setComment(item.getComment());
            scoreEntity.setIsObjective(0);
            questionScoreMapper.insert(scoreEntity);
            subjectiveScore = subjectiveScore.add(got);
        }

        taskEntity.setSubjectiveScore(subjectiveScore);
        taskEntity.setTotalScore(taskEntity.getObjectiveScore().add(subjectiveScore));
        taskEntity.setStatus(GradingTaskStatus.DONE.name());
        taskEntity.setGraderId(parseLong("graderId", graderId));
        taskEntity.setUpdatedAt(LocalDateTime.now());
        gradingTaskMapper.updateById(taskEntity);

        publishScore(taskEntity);

        List<QuestionScoreEntity> scoreEntities = questionScoreMapper.selectList(
                Wrappers.lambdaQuery(QuestionScoreEntity.class)
                        .eq(QuestionScoreEntity::getTaskId, taskLongId)
                        .orderByAsc(QuestionScoreEntity::getId)
        );
        return toTask(taskEntity, scoreEntities);
    }

    private BigDecimal calculateObjectiveScore(String sessionId) {
        int hash = Math.abs(sessionId.hashCode());
        return BigDecimal.valueOf(55 + (hash % 40)).setScale(2, RoundingMode.HALF_UP);
    }

    private void publishScore(GradingTaskEntity taskEntity) {
        ScorePublishedEvent event = new ScorePublishedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setExamId(String.valueOf(taskEntity.getExamId()));
        event.setSessionId(String.valueOf(taskEntity.getSessionId()));
        event.setUserId(String.valueOf(taskEntity.getUserId()));
        event.setTotalScore(taskEntity.getTotalScore().doubleValue());
        event.setPublishedAt(OffsetDateTime.now());
        rabbitTemplate.convertAndSend(RabbitConfig.EXAM_EXCHANGE, RabbitConfig.SCORE_PUBLISHED_ROUTING_KEY, event);
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
}
