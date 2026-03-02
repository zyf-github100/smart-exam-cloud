package com.smart.exam.exam.service;

import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.core.event.ExamSubmittedEvent;
import com.smart.exam.common.core.id.SnowflakeIdGenerator;
import com.smart.exam.exam.config.RabbitConfig;
import com.smart.exam.exam.dto.CreateExamRequest;
import com.smart.exam.exam.dto.SaveAnswersRequest;
import com.smart.exam.exam.model.Exam;
import com.smart.exam.exam.model.ExamSession;
import com.smart.exam.exam.model.ExamStatus;
import com.smart.exam.exam.model.SessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExamDomainService {

    private static final Logger log = LoggerFactory.getLogger(ExamDomainService.class);

    private final SnowflakeIdGenerator idGenerator;
    private final RabbitTemplate rabbitTemplate;
    private final Map<String, Exam> examStore = new ConcurrentHashMap<>();
    private final Map<String, ExamSession> sessionStore = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> answerStore = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> submitLock = new ConcurrentHashMap<>();

    public ExamDomainService(SnowflakeIdGenerator idGenerator, ObjectProvider<RabbitTemplate> rabbitTemplateProvider) {
        this.idGenerator = idGenerator;
        this.rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
    }

    public Exam createExam(CreateExamRequest request, String userId) {
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "startTime 必须早于 endTime");
        }
        Exam exam = new Exam();
        exam.setId(String.valueOf(idGenerator.nextId()));
        exam.setPaperId(request.getPaperId());
        exam.setTitle(request.getTitle());
        exam.setStartTime(request.getStartTime());
        exam.setEndTime(request.getEndTime());
        exam.setAntiCheatLevel(request.getAntiCheatLevel());
        exam.setStatus(ExamStatus.NOT_STARTED);
        exam.setCreatedBy(userId);
        examStore.put(exam.getId(), exam);
        return exam;
    }

    public Map<String, Object> startExam(String examId, String userId, String ip) {
        Exam exam = getExam(examId);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime()) || now.isAfter(exam.getEndTime())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前不在考试时间窗口内");
        }

        ExamSession session = new ExamSession();
        session.setId(String.valueOf(idGenerator.nextId()));
        session.setExamId(examId);
        session.setUserId(userId);
        session.setStartTime(now);
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setIpAtStart(ip);
        session.setSwitchScreenCount(0);
        session.setLastSaveTime(now);
        sessionStore.put(session.getId(), session);
        answerStore.put(session.getId(), new ConcurrentHashMap<>());

        long timeLimitSeconds = Math.max(1, Duration.between(now, exam.getEndTime()).toSeconds());
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", session.getId());
        payload.put("serverTime", now);
        payload.put("timeLimitSeconds", timeLimitSeconds);
        return payload;
    }

    public void saveAnswers(String sessionId, SaveAnswersRequest request, String userId) {
        ExamSession session = getSession(sessionId);
        if (!session.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "会话不属于当前用户");
        }
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前会话已不可编辑");
        }
        Map<String, Object> sessionAnswers = answerStore.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
        request.getAnswers().forEach(item -> sessionAnswers.put(item.getQuestionId(), item.getAnswerContent()));
        session.setLastSaveTime(LocalDateTime.now());
    }

    public Map<String, Object> submit(String sessionId, String userId) {
        if (!acquireSubmitLock(sessionId)) {
            throw new BizException(ErrorCode.CONFLICT, "请勿重复提交");
        }

        ExamSession session = getSession(sessionId);
        if (!session.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "会话不属于当前用户");
        }
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BizException(ErrorCode.CONFLICT, "会话已提交");
        }

        session.setStatus(SessionStatus.SUBMITTED);
        session.setSubmitTime(LocalDateTime.now());

        ExamSubmittedEvent event = new ExamSubmittedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setExamId(session.getExamId());
        event.setSessionId(session.getId());
        event.setUserId(session.getUserId());
        event.setSubmittedAt(OffsetDateTime.now());

        publishSubmittedEvent(event);

        return Map.of(
                "sessionId", sessionId,
                "status", session.getStatus().name(),
                "submittedAt", session.getSubmitTime()
        );
    }

    public Exam getExam(String examId) {
        Exam exam = examStore.get(examId);
        if (exam == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "考试不存在");
        }
        return exam;
    }

    private ExamSession getSession(String sessionId) {
        ExamSession session = sessionStore.get(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "考试会话不存在");
        }
        return session;
    }

    private boolean acquireSubmitLock(String sessionId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = submitLock.get(sessionId);
        if (expireTime != null && expireTime.isAfter(now)) {
            return false;
        }
        submitLock.put(sessionId, now.plusSeconds(30));
        return true;
    }

    private void publishSubmittedEvent(ExamSubmittedEvent event) {
        if (rabbitTemplate == null) {
            log.warn("RabbitTemplate unavailable, skip publish exam.submitted event: {}", event.getEventId());
            return;
        }
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXAM_EXCHANGE, RabbitConfig.EXAM_SUBMITTED_ROUTING_KEY, event);
        } catch (Exception ex) {
            log.error("Publish exam.submitted failed: {}", ex.getMessage(), ex);
        }
    }
}

