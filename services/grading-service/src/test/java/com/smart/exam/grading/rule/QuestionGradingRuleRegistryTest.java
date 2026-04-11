package com.smart.exam.grading.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.exam.grading.model.scoring.QuestionSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionGradingRuleRegistryTest {

    private static final BigDecimal DEFAULT_MAX_SCORE = BigDecimal.valueOf(5).setScale(2, RoundingMode.HALF_UP);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QuestionGradingRuleRegistry registry = new QuestionGradingRuleRegistry(List.of(
            new SingleChoiceQuestionGradingRule(),
            new MultiChoiceQuestionGradingRule(),
            new JudgeQuestionGradingRule(),
            new FillBlankQuestionGradingRule()
    ));

    @Test
    void shouldExposeSupportedObjectiveQuestionTypes() {
        assertTrue(registry.supports("single"));
        assertTrue(registry.supports("MULTI"));
        assertTrue(registry.supports("Judge"));
        assertTrue(registry.supports("fill"));
        assertFalse(registry.supports("SHORT"));
    }

    @Test
    void shouldGradeSingleChoiceWithCaseInsensitiveMatch() throws Exception {
        QuestionGradingResult result = grade("SINGLE", "A", objectMapper.readTree("\"a\""));

        assertTrue(result.objective());
        assertFalse(result.manualRequired());
        assertEquals(DEFAULT_MAX_SCORE, result.gotScore());
        assertEquals("AUTO_CORRECT", result.comment());
    }

    @Test
    void shouldGradeMultiChoiceWithoutOrderSensitivity() throws Exception {
        QuestionGradingResult result = grade("MULTI", "A,B", objectMapper.readTree("[\"B\",\"A\"]"));

        assertTrue(result.objective());
        assertFalse(result.manualRequired());
        assertEquals(DEFAULT_MAX_SCORE, result.gotScore());
        assertEquals("AUTO_CORRECT", result.comment());
    }

    @Test
    void shouldGradeJudgeWithBooleanAliases() throws Exception {
        QuestionGradingResult result = grade("JUDGE", "true", objectMapper.readTree("\"1\""));

        assertTrue(result.objective());
        assertFalse(result.manualRequired());
        assertEquals(DEFAULT_MAX_SCORE, result.gotScore());
        assertEquals("AUTO_CORRECT", result.comment());
    }

    @Test
    void shouldGradeFillWithTrimmedCaseInsensitiveText() throws Exception {
        QuestionGradingResult result = grade("FILL", " Java ", objectMapper.readTree("\"java\""));

        assertTrue(result.objective());
        assertFalse(result.manualRequired());
        assertEquals(DEFAULT_MAX_SCORE, result.gotScore());
        assertEquals("AUTO_CORRECT", result.comment());
    }

    @Test
    void shouldFallbackToManualForUnsupportedQuestionType() {
        QuestionGradingResult result = grade("SHORT", "Any answer", null);

        assertFalse(result.objective());
        assertTrue(result.manualRequired());
        assertEquals(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), result.gotScore());
        assertEquals("PENDING_MANUAL", result.comment());
    }

    @Test
    void shouldFailFastWhenDuplicateQuestionTypeRulesAreRegistered() {
        QuestionGradingRule duplicateSingleRule = new QuestionGradingRule() {
            @Override
            public String getQuestionType() {
                return "single";
            }

            @Override
            public QuestionGradingResult grade(QuestionGradingContext context) {
                return QuestionGradingResult.autoWrong();
            }
        };

        assertThrows(IllegalStateException.class, () -> new QuestionGradingRuleRegistry(List.of(
                new SingleChoiceQuestionGradingRule(),
                duplicateSingleRule
        )));
    }

    private QuestionGradingResult grade(String type, String standardAnswer, JsonNode answerNode) {
        QuestionSnapshot question = new QuestionSnapshot();
        question.setType(type);
        question.setAnswer(standardAnswer);
        return registry.grade(question, answerNode, DEFAULT_MAX_SCORE);
    }
}
