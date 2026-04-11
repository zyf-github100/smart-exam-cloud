package com.smart.exam.grading.rule;

import org.springframework.stereotype.Component;

@Component
public class JudgeQuestionGradingRule implements QuestionGradingRule {

    @Override
    public String getQuestionType() {
        return "JUDGE";
    }

    @Override
    public QuestionGradingResult grade(QuestionGradingContext context) {
        String expectedRaw = context.question() == null ? null : context.question().getAnswer();
        Boolean expected = QuestionAnswerSupport.parseBooleanValue(expectedRaw);
        Boolean actual = QuestionAnswerSupport.parseBooleanValue(
                QuestionAnswerSupport.extractFirstScalar(context.answerNode())
        );
        return expected != null && expected.equals(actual)
                ? QuestionGradingResult.autoCorrect(context.maxScore())
                : QuestionGradingResult.autoWrong();
    }
}
