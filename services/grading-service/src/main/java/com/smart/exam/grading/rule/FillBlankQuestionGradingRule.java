package com.smart.exam.grading.rule;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FillBlankQuestionGradingRule implements QuestionGradingRule {

    @Override
    public String getQuestionType() {
        return "FILL";
    }

    @Override
    public QuestionGradingResult grade(QuestionGradingContext context) {
        String expected = QuestionAnswerSupport.normalizeFillAnswer(
                context.question() == null ? null : context.question().getAnswer()
        );
        String actual = QuestionAnswerSupport.normalizeFillAnswer(
                QuestionAnswerSupport.extractFirstScalar(context.answerNode())
        );
        return StringUtils.hasText(expected) && expected.equalsIgnoreCase(actual)
                ? QuestionGradingResult.autoCorrect(context.maxScore())
                : QuestionGradingResult.autoWrong();
    }
}
