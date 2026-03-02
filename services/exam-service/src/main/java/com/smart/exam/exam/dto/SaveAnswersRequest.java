package com.smart.exam.exam.dto;

import com.smart.exam.exam.model.AnswerItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class SaveAnswersRequest {

    @NotEmpty
    @Valid
    private List<AnswerItem> answers;

    public List<AnswerItem> getAnswers() {
        return answers;
    }

    public void setAnswers(List<AnswerItem> answers) {
        this.answers = answers;
    }
}

