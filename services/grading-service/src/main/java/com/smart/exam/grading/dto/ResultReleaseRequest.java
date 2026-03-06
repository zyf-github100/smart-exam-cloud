package com.smart.exam.grading.dto;

import jakarta.validation.constraints.NotNull;

public class ResultReleaseRequest {

    @NotNull
    private Boolean released;

    public Boolean getReleased() {
        return released;
    }

    public void setReleased(Boolean released) {
        this.released = released;
    }
}
