package com.smart.exam.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.exam.analysis.entity.ScoreEntity;
import com.smart.exam.analysis.entity.SessionQuestionScoreEntity;
import com.smart.exam.analysis.mapper.ExamReadMapper;
import com.smart.exam.analysis.mapper.ScoreMapper;
import com.smart.exam.analysis.mapper.SessionQuestionScoreMapper;
import com.smart.exam.analysis.model.StudentScoreItem;
import com.smart.exam.common.core.event.ScorePublishedEvent;
import com.smart.exam.common.core.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportDomainServiceTest {

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private ExamReadMapper examReadMapper;

    @Mock
    private ScoreMapper scoreMapper;

    @Mock
    private SessionQuestionScoreMapper sessionQuestionScoreMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ReportDomainService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new ReportDomainService(
                idGenerator,
                examReadMapper,
                scoreMapper,
                sessionQuestionScoreMapper,
                redisTemplate,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void onScorePublishedSkipsRepeatedEvent() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        service.onScorePublished(event());

        verifyNoInteractions(scoreMapper, sessionQuestionScoreMapper, idGenerator);
    }

    @Test
    void onScorePublishedCreatesScoreAndQuestionScoreRowsForNewSession() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(idGenerator.nextId()).thenReturn(700L, 701L, 702L);
        when(redisTemplate.keys("report:question-accuracy:100:*")).thenReturn(Set.of());
        when(redisTemplate.keys("report:score-sheet:100:*")).thenReturn(Set.of());

        service.onScorePublished(event());

        ArgumentCaptor<ScoreEntity> scoreCaptor = ArgumentCaptor.forClass(ScoreEntity.class);
        verify(scoreMapper).insert(scoreCaptor.capture());
        ScoreEntity score = scoreCaptor.getValue();
        assertEquals(700L, score.getId());
        assertEquals(100L, score.getExamId());
        assertEquals(200L, score.getSessionId());
        assertEquals(300L, score.getUserId());
        assertEquals(0, BigDecimal.valueOf(88.5).setScale(2).compareTo(score.getTotalScore()));

        ArgumentCaptor<SessionQuestionScoreEntity> questionCaptor = ArgumentCaptor.forClass(SessionQuestionScoreEntity.class);
        verify(sessionQuestionScoreMapper).delete(any());
        verify(sessionQuestionScoreMapper, org.mockito.Mockito.times(2)).insert(questionCaptor.capture());
        List<SessionQuestionScoreEntity> insertedQuestions = questionCaptor.getAllValues();
        assertEquals(List.of(701L, 702L), insertedQuestions.stream().map(SessionQuestionScoreEntity::getId).toList());
        assertEquals(List.of(11L, 12L), insertedQuestions.stream().map(SessionQuestionScoreEntity::getQuestionId).toList());
        assertEquals(List.of(1, 0), insertedQuestions.stream().map(SessionQuestionScoreEntity::getIsObjective).toList());

        verify(redisTemplate).delete("report:score-distribution:100");
    }

    @Test
    void scoreSheetCapsLimitAndAssignsRanks() {
        when(examReadMapper.selectExamOwnerById(100L)).thenReturn(2001L);
        when(scoreMapper.selectScoreSheet(100L, null, 1000)).thenReturn(List.of(
                studentScore(3001L, "student001", new BigDecimal("88.50")),
                studentScore(3002L, "student002", new BigDecimal("76.00"))
        ));

        Map<String, Object> payload = service.scoreSheet("100", null, 5000, "2001", "TEACHER");

        assertEquals("100", payload.get("examId"));
        assertEquals(1000, payload.get("limit"));
        assertEquals(2, payload.get("total"));
        @SuppressWarnings("unchecked")
        List<StudentScoreItem> records = (List<StudentScoreItem>) payload.get("records");
        assertEquals(List.of(1, 2), records.stream().map(StudentScoreItem::getRank).toList());
        assertEquals(List.of("student001", "student002"), records.stream().map(StudentScoreItem::getUsername).toList());
        verify(scoreMapper).selectScoreSheet(100L, null, 1000);
    }

    private ScorePublishedEvent event() {
        ScorePublishedEvent event = new ScorePublishedEvent();
        event.setEventId("evt-1");
        event.setExamId("100");
        event.setSessionId("200");
        event.setUserId("300");
        event.setTotalScore(88.5);
        event.setQuestionScores(List.of(
                questionScore("11", 5, 5, true),
                questionScore("12", 10, 6.5, false)
        ));
        return event;
    }

    private ScorePublishedEvent.QuestionScorePayload questionScore(String questionId, double maxScore, double gotScore, boolean objective) {
        ScorePublishedEvent.QuestionScorePayload payload = new ScorePublishedEvent.QuestionScorePayload();
        payload.setQuestionId(questionId);
        payload.setMaxScore(maxScore);
        payload.setGotScore(gotScore);
        payload.setObjective(objective);
        return payload;
    }

    private StudentScoreItem studentScore(Long sessionId, String username, BigDecimal totalScore) {
        StudentScoreItem item = new StudentScoreItem();
        item.setSessionId(sessionId);
        item.setUsername(username);
        item.setTotalScore(totalScore);
        return item;
    }
}
