package com.smart.exam.grading.rule;

public interface QuestionGradingRule {

    String getQuestionType();

    QuestionGradingResult grade(QuestionGradingContext context);
}
