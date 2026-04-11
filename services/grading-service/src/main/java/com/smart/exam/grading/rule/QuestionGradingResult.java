package com.smart.exam.grading.rule;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record QuestionGradingResult(
        boolean objective,
        boolean manualRequired,
        BigDecimal gotScore,
        String comment) {

    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public static QuestionGradingResult autoCorrect(BigDecimal maxScore) {
        return new QuestionGradingResult(true, false, normalizeScore(maxScore), "AUTO_CORRECT");
    }

    public static QuestionGradingResult autoWrong() {
        return new QuestionGradingResult(true, false, ZERO_SCORE, "AUTO_WRONG");
    }

    public static QuestionGradingResult pendingManual() {
        return new QuestionGradingResult(false, true, ZERO_SCORE, "PENDING_MANUAL");
    }

    private static BigDecimal normalizeScore(BigDecimal score) {
        return score == null ? ZERO_SCORE : score.setScale(2, RoundingMode.HALF_UP);
    }
}
