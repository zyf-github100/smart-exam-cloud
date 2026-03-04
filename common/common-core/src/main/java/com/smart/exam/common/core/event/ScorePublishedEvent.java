package com.smart.exam.common.core.event;

import java.time.OffsetDateTime;
import java.util.List;

public class ScorePublishedEvent {

    private String eventId;
    private String examId;
    private String sessionId;
    private String userId;
    private double totalScore;
    private OffsetDateTime publishedAt;
    private List<QuestionScorePayload> questionScores;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getExamId() {
        return examId;
    }

    public void setExamId(String examId) {
        this.examId = examId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(double totalScore) {
        this.totalScore = totalScore;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public List<QuestionScorePayload> getQuestionScores() {
        return questionScores;
    }

    public void setQuestionScores(List<QuestionScorePayload> questionScores) {
        this.questionScores = questionScores;
    }

    public static class QuestionScorePayload {

        private String questionId;
        private double maxScore;
        private double gotScore;
        private boolean objective;

        public String getQuestionId() {
            return questionId;
        }

        public void setQuestionId(String questionId) {
            this.questionId = questionId;
        }

        public double getMaxScore() {
            return maxScore;
        }

        public void setMaxScore(double maxScore) {
            this.maxScore = maxScore;
        }

        public double getGotScore() {
            return gotScore;
        }

        public void setGotScore(double gotScore) {
            this.gotScore = gotScore;
        }

        public boolean isObjective() {
            return objective;
        }

        public void setObjective(boolean objective) {
            this.objective = objective;
        }
    }
}
