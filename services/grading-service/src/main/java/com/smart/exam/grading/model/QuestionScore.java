package com.smart.exam.grading.model;

public class QuestionScore {

    private String questionId;
    private Double maxScore;
    private Double gotScore;
    private String comment;
    private Boolean objective;

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public Double getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Double maxScore) {
        this.maxScore = maxScore;
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

    public Boolean getObjective() {
        return objective;
    }

    public void setObjective(Boolean objective) {
        this.objective = objective;
    }
}

