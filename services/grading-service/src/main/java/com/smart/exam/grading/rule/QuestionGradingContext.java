package com.smart.exam.grading.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.smart.exam.grading.model.scoring.QuestionSnapshot;

import java.math.BigDecimal;

public record QuestionGradingContext(
        QuestionSnapshot question,
        JsonNode answerNode,
        BigDecimal maxScore) {
}
