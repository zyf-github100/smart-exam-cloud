package com.smart.exam.grading.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class GradingTask {

    private String id;
    private String examId;
    private String sessionId;
    private String userId;
    private GradingTaskStatus status;
    private Double objectiveScore;
    private Double subjectiveScore;
    private Double totalScore;
    private String graderId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<QuestionScore> questionScores = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public GradingTaskStatus getStatus() {
        return status;
    }

    public void setStatus(GradingTaskStatus status) {
        this.status = status;
    }

    public Double getObjectiveScore() {
        return objectiveScore;
    }

    public void setObjectiveScore(Double objectiveScore) {
        this.objectiveScore = objectiveScore;
    }

    public Double getSubjectiveScore() {
        return subjectiveScore;
    }

    public void setSubjectiveScore(Double subjectiveScore) {
        this.subjectiveScore = subjectiveScore;
    }

    public Double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Double totalScore) {
        this.totalScore = totalScore;
    }

    public String getGraderId() {
        return graderId;
    }

    public void setGraderId(String graderId) {
        this.graderId = graderId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<QuestionScore> getQuestionScores() {
        return questionScores;
    }

    public void setQuestionScores(List<QuestionScore> questionScores) {
        this.questionScores = questionScores;
    }
}

