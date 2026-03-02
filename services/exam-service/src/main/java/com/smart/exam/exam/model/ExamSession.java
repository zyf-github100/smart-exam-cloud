package com.smart.exam.exam.model;

import java.time.LocalDateTime;

public class ExamSession {

    private String id;
    private String examId;
    private String userId;
    private LocalDateTime startTime;
    private LocalDateTime submitTime;
    private SessionStatus status;
    private String ipAtStart;
    private Integer switchScreenCount;
    private LocalDateTime lastSaveTime;

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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getIpAtStart() {
        return ipAtStart;
    }

    public void setIpAtStart(String ipAtStart) {
        this.ipAtStart = ipAtStart;
    }

    public Integer getSwitchScreenCount() {
        return switchScreenCount;
    }

    public void setSwitchScreenCount(Integer switchScreenCount) {
        this.switchScreenCount = switchScreenCount;
    }

    public LocalDateTime getLastSaveTime() {
        return lastSaveTime;
    }

    public void setLastSaveTime(LocalDateTime lastSaveTime) {
        this.lastSaveTime = lastSaveTime;
    }
}

