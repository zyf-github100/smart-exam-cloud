package com.smart.exam.question.model;

import java.util.List;

public class Paper {

    private String id;
    private String name;
    private Integer totalScore;
    private Integer timeLimitMinutes;
    private String createdBy;
    private List<PaperQuestion> questions;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public Integer getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public void setTimeLimitMinutes(Integer timeLimitMinutes) {
        this.timeLimitMinutes = timeLimitMinutes;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public List<PaperQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(List<PaperQuestion> questions) {
        this.questions = questions;
    }
}

