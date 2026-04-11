package com.smart.exam.grading.rule;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SingleChoiceQuestionGradingRule implements QuestionGradingRule {

    @Override
    public String getQuestionType() {
        return "SINGLE";
    }

    @Override
    public QuestionGradingResult grade(QuestionGradingContext context) {
        String expected = QuestionAnswerSupport.normalizeToken(
                context.question() == null ? null : context.question().getAnswer()
        );
        String actual = QuestionAnswerSupport.normalizeToken(
                QuestionAnswerSupport.extractFirstScalar(context.answerNode())
        );
        return StringUtils.hasText(expected) && expected.equals(actual)
                ? QuestionGradingResult.autoCorrect(context.maxScore())
                : QuestionGradingResult.autoWrong();
    }
}
