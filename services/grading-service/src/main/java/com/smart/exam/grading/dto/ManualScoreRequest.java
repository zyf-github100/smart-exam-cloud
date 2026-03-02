package com.smart.exam.grading.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class ManualScoreRequest {

    @NotEmpty
    private List<ManualScoreItem> scores;

    public List<ManualScoreItem> getScores() {
        return scores;
    }

    public void setScores(List<ManualScoreItem> scores) {
        this.scores = scores;
    }

    public static class ManualScoreItem {
        @NotNull
        private String questionId;
        @NotNull
        private Double gotScore;
        private String comment;

        public String getQuestionId() {
            return questionId;
        }

        public void setQuestionId(String questionId) {
            this.questionId = questionId;
        }

        public Double getGotScore() {
            return gotScore;
        }

        public void setGotScore(Double gotScore) {
            this.gotScore = gotScore;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }
    }
}

