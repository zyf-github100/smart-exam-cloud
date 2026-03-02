package com.smart.exam.question.dto;

import com.smart.exam.question.model.PaperQuestion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class CreatePaperRequest {

    @NotBlank
    private String name;

    @NotNull
    private Integer timeLimitMinutes;

    @NotEmpty
    private List<PaperQuestion> questions;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public void setTimeLimitMinutes(Integer timeLimitMinutes) {
        this.timeLimitMinutes = timeLimitMinutes;
    }

    public List<PaperQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(List<PaperQuestion> questions) {
        this.questions = questions;
    }
}

