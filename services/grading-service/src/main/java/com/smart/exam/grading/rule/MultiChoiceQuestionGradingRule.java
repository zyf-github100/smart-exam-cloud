package com.smart.exam.grading.rule;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class MultiChoiceQuestionGradingRule implements QuestionGradingRule {

    @Override
    public String getQuestionType() {
        return "MULTI";
    }

    @Override
    public QuestionGradingResult grade(QuestionGradingContext context) {
        String expectedRaw = context.question() == null ? null : context.question().getAnswer();
        Set<String> expectedSet = QuestionAnswerSupport.splitTokens(expectedRaw);
        Set<String> actualSet = QuestionAnswerSupport.readMultiAnswerTokens(context.answerNode());
        return !expectedSet.isEmpty() && expectedSet.equals(actualSet)
                ? QuestionGradingResult.autoCorrect(context.maxScore())
                : QuestionGradingResult.autoWrong();
    }
}
