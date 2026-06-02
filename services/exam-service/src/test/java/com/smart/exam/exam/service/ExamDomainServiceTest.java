package com.smart.exam.exam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.core.id.SnowflakeIdGenerator;
import com.smart.exam.common.web.audit.AuditLogService;
import com.smart.exam.exam.config.AntiCheatProperties;
import com.smart.exam.exam.entity.ExamEntity;
import com.smart.exam.exam.entity.ExamSessionEntity;
import com.smart.exam.exam.mapper.AnswerMapper;
import com.smart.exam.exam.mapper.ExamMapper;
import com.smart.exam.exam.mapper.ExamSessionMapper;
import com.smart.exam.exam.mapper.ExamTargetMapper;
import com.smart.exam.exam.mapper.QuestionReadMapper;
import com.smart.exam.exam.mapper.SessionRiskEventMapper;
import com.smart.exam.exam.mapper.SessionRiskSummaryMapper;
import com.smart.exam.exam.mapper.UserReadMapper;
import com.smart.exam.exam.model.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamDomainServiceTest {

    private final SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ExamMapper examMapper = mock(ExamMapper.class);
    private final ExamTargetMapper examTargetMapper = mock(ExamTargetMapper.class);
    private final ExamSessionMapper examSessionMapper = mock(ExamSessionMapper.class);
    private final AnswerMapper answerMapper = mock(AnswerMapper.class);
    private final QuestionReadMapper questionReadMapper = mock(QuestionReadMapper.class);
    private final UserReadMapper userReadMapper = mock(UserReadMapper.class);
    private final SessionRiskEventMapper sessionRiskEventMapper = mock(SessionRiskEventMapper.class);
    private final SessionRiskSummaryMapper sessionRiskSummaryMapper = mock(SessionRiskSummaryMapper.class);
    private final AntiCheatProperties antiCheatProperties = new AntiCheatProperties();
    private final AntiCheatRuleEngine antiCheatRuleEngine = new AntiCheatRuleEngine(antiCheatProperties);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private ExamDomainService service;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RabbitTemplate> rabbitTemplateProvider = mock(ObjectProvider.class);
        when(rabbitTemplateProvider.getIfAvailable()).thenReturn(rabbitTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        service = new ExamDomainService(
                idGenerator,
                rabbitTemplateProvider,
                examMapper,
                examTargetMapper,
                examSessionMapper,
                answerMapper,
                questionReadMapper,
                userReadMapper,
                sessionRiskEventMapper,
                sessionRiskSummaryMapper,
                antiCheatRuleEngine,
                antiCheatProperties,
                redisTemplate,
                new ObjectMapper(),
                auditLogService
        );
    }

    @Test
    void submitDoesNotPublishEventWhenConditionalStatusUpdateFails() {
        ExamSessionEntity session = new ExamSessionEntity();
        session.setId(9001L);
        session.setExamId(8001L);
        session.setUserId(31001L);
        session.setStatus(SessionStatus.IN_PROGRESS.name());
        when(examSessionMapper.selectById(9001L)).thenReturn(session);

        ExamEntity exam = new ExamEntity();
        exam.setId(8001L);
        exam.setEndTime(LocalDateTime.now().plusMinutes(10));
        when(examMapper.selectById(8001L)).thenReturn(exam);
        when(examSessionMapper.updateStatusIfMatched(
                9001L,
                SessionStatus.IN_PROGRESS.name(),
                SessionStatus.SUBMITTED.name(),
                session.getSubmitTime()
        )).thenReturn(0);

        BizException exception = assertThrows(BizException.class, () -> service.submit(
                "9001",
                "31001",
                "STUDENT",
                "127.0.0.1",
                "JUnit"
        ));

        assertEquals(ErrorCode.CONFLICT.getCode(), exception.getCode());
        verify(rabbitTemplate, never()).convertAndSend(
                anyString(),
                anyString(),
                any(Object.class),
                any(CorrelationData.class)
        );
    }
}
