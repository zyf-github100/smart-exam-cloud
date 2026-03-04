package com.smart.exam.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("a_session_question_score")
public class SessionQuestionScoreEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long examId;
    private Long sessionId;
    private Long questionId;
    private BigDecimal maxScore;
    private BigDecimal gotScore;
    private Integer isObjective;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExamId() {
        return examId;
    }

    public void setExamId(Long examId) {
        this.examId = examId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    public BigDecimal getGotScore() {
        return gotScore;
    }

    public void setGotScore(BigDecimal gotScore) {
        this.gotScore = gotScore;
    }

    public Integer getIsObjective() {
        return isObjective;
    }

    public void setIsObjective(Integer isObjective) {
        this.isObjective = isObjective;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

