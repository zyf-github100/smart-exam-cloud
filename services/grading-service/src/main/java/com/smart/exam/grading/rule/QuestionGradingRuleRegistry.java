package com.smart.exam.grading.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.smart.exam.grading.model.scoring.QuestionSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QuestionGradingRuleRegistry {

    private final Map<String, QuestionGradingRule> rulesByType;

    public QuestionGradingRuleRegistry(List<QuestionGradingRule> rules) {
        Map<String, QuestionGradingRule> index = new LinkedHashMap<>();
        for (QuestionGradingRule rule : rules) {
            String questionType = QuestionAnswerSupport.normalizeToken(rule.getQuestionType());
            QuestionGradingRule existing = index.putIfAbsent(questionType, rule);
            if (existing != null) {
                throw new IllegalStateException("Duplicate grading rule for question type: " + questionType);
            }
        }
        this.rulesByType = Map.copyOf(index);
    }

    public boolean supports(String questionType) {
        return rulesByType.containsKey(QuestionAnswerSupport.normalizeToken(questionType));
    }

    public QuestionGradingResult grade(QuestionSnapshot question, JsonNode answerNode, BigDecimal maxScore) {
        String questionType = question == null ? null : question.getType();
        QuestionGradingRule rule = rulesByType.get(QuestionAnswerSupport.normalizeToken(questionType));
        if (rule == null) {
            return QuestionGradingResult.pendingManual();
        }
        return rule.grade(new QuestionGradingContext(question, answerNode, maxScore));
    }
}
