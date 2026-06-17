package com.smart.exam.question.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.core.id.SnowflakeIdGenerator;
import com.smart.exam.common.web.audit.AuditActions;
import com.smart.exam.common.web.audit.AuditLogCommand;
import com.smart.exam.common.web.audit.AuditLogService;
import com.smart.exam.question.dto.CreatePaperRequest;
import com.smart.exam.question.dto.CreateQuestionRequest;
import com.smart.exam.question.entity.PaperEntity;
import com.smart.exam.question.entity.PaperQuestionEntity;
import com.smart.exam.question.entity.QuestionEntity;
import com.smart.exam.question.mapper.PaperMapper;
import com.smart.exam.question.mapper.PaperQuestionMapper;
import com.smart.exam.question.mapper.QuestionMapper;
import com.smart.exam.question.model.PaperQuestion;
import com.smart.exam.question.model.Question;
import com.smart.exam.question.model.QuestionOption;
import com.smart.exam.question.model.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionDomainServiceTest {

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private PaperMapper paperMapper;

    @Mock
    private PaperQuestionMapper paperQuestionMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AuditLogService auditLogService;

    private QuestionDomainService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        service = new QuestionDomainService(
                idGenerator,
                questionMapper,
                paperMapper,
                paperQuestionMapper,
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                auditLogService
        );
    }

    @Test
    void createQuestionNormalizesChoicePayloadAndWritesAudit() {
        when(idGenerator.nextId()).thenReturn(1001L);

        CreateQuestionRequest request = new CreateQuestionRequest();
        request.setType(QuestionType.MULTI);
        request.setStem(" What is correct? ");
        request.setDifficulty(3);
        request.setKnowledgePoint(" OOP ");
        request.setAnalysis(" Explanation ");
        request.setAnswer("a， b a");
        request.setOptions(List.of(option("a", "extends"), option("b", "implements")));

        Question result = service.createQuestion(request, "2001", "TEACHER", "127.0.0.1", "JUnit");

        ArgumentCaptor<QuestionEntity> entityCaptor = ArgumentCaptor.forClass(QuestionEntity.class);
        verify(questionMapper).insert(entityCaptor.capture());
        QuestionEntity saved = entityCaptor.getValue();
        assertEquals("MULTI", saved.getType());
        assertEquals("What is correct?", saved.getStem());
        assertEquals("A,B", saved.getAnswer());
        assertEquals(2001L, saved.getCreatedBy());
        assertTrue(saved.getOptionsJson().contains("\"key\":\"A\""));
        assertTrue(saved.getOptionsJson().contains("\"key\":\"B\""));

        assertEquals("1001", result.getId());
        assertEquals("A,B", result.getAnswer());
        assertEquals(2, result.getOptions().size());
        assertEquals("A", result.getOptions().get(0).getKey());
        assertEquals("B", result.getOptions().get(1).getKey());

        ArgumentCaptor<AuditLogCommand> auditCaptor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogService).record(auditCaptor.capture());
        AuditLogCommand command = auditCaptor.getValue();
        assertEquals(AuditActions.QUESTION_CREATED, command.action());
        assertEquals("2001", command.operatorId());
        assertEquals("127.0.0.1", command.ip());
    }

    @Test
    void createQuestionRejectsDuplicateChoiceKeysAfterNormalization() {
        CreateQuestionRequest request = new CreateQuestionRequest();
        request.setType(QuestionType.SINGLE);
        request.setStem("Duplicate options");
        request.setDifficulty(2);
        request.setAnswer("A");
        request.setOptions(List.of(option("a", "first"), option("A", "second")));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.createQuestion(request, "2001", "TEACHER", "127.0.0.1", "JUnit")
        );

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("Duplicate option key"));
        verify(questionMapper, never()).insert(any(QuestionEntity.class));
        verify(auditLogService, never()).record(any(AuditLogCommand.class));
    }

    @Test
    void createPaperRejectsQuestionOrderThatBreaksQuestionTypeSequence() {
        when(questionMapper.selectList(any())).thenReturn(List.of(
                questionEntity(11L, 2001L, "JUDGE"),
                questionEntity(12L, 2001L, "SINGLE")
        ));

        CreatePaperRequest request = new CreatePaperRequest();
        request.setName("Java Basics");
        request.setTimeLimitMinutes(60);
        request.setQuestions(List.of(
                paperQuestion("11", 10, 1),
                paperQuestion("12", 10, 2)
        ));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.createPaper(request, "2001", "TEACHER", "127.0.0.1", "JUnit")
        );

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("Paper question order"));
        verify(paperMapper, never()).insert(any(PaperEntity.class));
        verify(paperQuestionMapper, never()).insert(any(PaperQuestionEntity.class));
        verify(auditLogService, never()).record(any(AuditLogCommand.class));
    }

    private QuestionOption option(String key, String text) {
        QuestionOption option = new QuestionOption();
        option.setKey(key);
        option.setText(text);
        return option;
    }

    private PaperQuestion paperQuestion(String questionId, int score, int orderNo) {
        PaperQuestion question = new PaperQuestion();
        question.setQuestionId(questionId);
        question.setScore(score);
        question.setOrderNo(orderNo);
        return question;
    }

    private QuestionEntity questionEntity(Long id, Long createdBy, String type) {
        QuestionEntity entity = new QuestionEntity();
        entity.setId(id);
        entity.setCreatedBy(createdBy);
        entity.setType(type);
        entity.setStem("Stem-" + id);
        entity.setAnswer("A");
        entity.setDifficulty(1);
        entity.setOptionsJson("[]");
        return entity;
    }
}
