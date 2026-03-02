package com.smart.exam.grading.service;

import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.core.event.ExamSubmittedEvent;
import com.smart.exam.common.core.event.ScorePublishedEvent;
import com.smart.exam.common.core.id.SnowflakeIdGenerator;
import com.smart.exam.grading.config.RabbitConfig;
import com.smart.exam.grading.dto.ManualScoreRequest;
import com.smart.exam.grading.model.GradingTask;
import com.smart.exam.grading.model.GradingTaskStatus;
import com.smart.exam.grading.model.QuestionScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GradingDomainService {

    private static final Logger log = LoggerFactory.getLogger(GradingDomainService.class);

    private final SnowflakeIdGenerator idGenerator;
    private final RabbitTemplate rabbitTemplate;
    private final Map<String, GradingTask> taskStore = new ConcurrentHashMap<>();
    private final Set<String> consumedEventIds = ConcurrentHashMap.newKeySet();

    public GradingDomainService(SnowflakeIdGenerator idGenerator, RabbitTemplate rabbitTemplate) {
        this.idGenerator = idGenerator;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void onExamSubmitted(ExamSubmittedEvent event) {
        if (!consumedEventIds.add(event.getEventId())) {
            log.info("Skip duplicate exam submitted event: {}", event.getEventId());
            return;
        }

        GradingTask task = new GradingTask();
        task.setId(String.valueOf(idGenerator.nextId()));
        task.setExamId(event.getExamId());
        task.setSessionId(event.getSessionId());
        task.setUserId(event.getUserId());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        double objectiveScore = calculateObjectiveScore(event.getSessionId());
        task.setObjectiveScore(objectiveScore);
        task.setQuestionScores(new ArrayList<>());

        // 演示用：偶数会话进入人工阅卷，奇数会话自动完成
        boolean manualRequired = Math.abs(event.getSessionId().hashCode()) % 2 == 0;
        if (manualRequired) {
            task.setStatus(GradingTaskStatus.MANUAL_REQUIRED);
            task.setSubjectiveScore(0.0);
            task.setTotalScore(objectiveScore);
        } else {
            task.setStatus(GradingTaskStatus.AUTO_DONE);
            task.setSubjectiveScore(0.0);
            task.setTotalScore(objectiveScore);
            publishScore(task);
        }

        taskStore.put(task.getId(), task);
    }

    public Collection<GradingTask> listTasks(String status) {
        if (status == null || status.isBlank()) {
            return taskStore.values();
        }
        GradingTaskStatus taskStatus = GradingTaskStatus.valueOf(status);
        return taskStore.values().stream().filter(t -> t.getStatus() == taskStatus).toList();
    }

    public GradingTask manualScore(String taskId, ManualScoreRequest request, String graderId) {
        GradingTask task = taskStore.get(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "阅卷任务不存在");
        }
        if (task.getStatus() != GradingTaskStatus.MANUAL_REQUIRED) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该任务当前不可人工阅卷");
        }

        double subjectiveScore = 0.0;
        for (ManualScoreRequest.ManualScoreItem item : request.getScores()) {
            QuestionScore detail = new QuestionScore();
            detail.setQuestionId(item.getQuestionId());
            detail.setGotScore(item.getGotScore());
            detail.setComment(item.getComment());
            detail.setObjective(false);
            task.getQuestionScores().add(detail);
            subjectiveScore += item.getGotScore();
        }

        task.setSubjectiveScore(subjectiveScore);
        task.setTotalScore(task.getObjectiveScore() + subjectiveScore);
        task.setStatus(GradingTaskStatus.DONE);
        task.setGraderId(graderId);
        task.setUpdatedAt(LocalDateTime.now());
        publishScore(task);
        return task;
    }

    private double calculateObjectiveScore(String sessionId) {
        int hash = Math.abs(sessionId.hashCode());
        return 55 + (hash % 40);
    }

    private void publishScore(GradingTask task) {
        ScorePublishedEvent event = new ScorePublishedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setExamId(task.getExamId());
        event.setSessionId(task.getSessionId());
        event.setUserId(task.getUserId());
        event.setTotalScore(task.getTotalScore());
        event.setPublishedAt(OffsetDateTime.now());
        rabbitTemplate.convertAndSend(RabbitConfig.EXAM_EXCHANGE, RabbitConfig.SCORE_PUBLISHED_ROUTING_KEY, event);
    }
}

