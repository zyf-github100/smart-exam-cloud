package com.smart.exam.exam.controller;

import com.smart.exam.common.core.model.ApiResponse;
import com.smart.exam.exam.dto.CreateExamRequest;
import com.smart.exam.exam.dto.SaveAnswersRequest;
import com.smart.exam.exam.model.Exam;
import com.smart.exam.exam.service.ExamDomainService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ExamController {

    private final ExamDomainService examDomainService;

    public ExamController(ExamDomainService examDomainService) {
        this.examDomainService = examDomainService;
    }

    @PostMapping("/exams")
    public ApiResponse<Exam> createExam(
            @Valid @RequestBody CreateExamRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "20001") String userId) {
        return ApiResponse.ok(examDomainService.createExam(request, userId));
    }

    @PostMapping("/exams/{examId}/start")
    public ApiResponse<Map<String, Object>> startExam(
            @PathVariable String examId,
            @RequestHeader(value = "X-User-Id", defaultValue = "30001") String userId,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        return ApiResponse.ok(examDomainService.startExam(examId, userId, ip));
    }

    @PutMapping("/sessions/{sessionId}/answers")
    public ApiResponse<Void> saveAnswers(
            @PathVariable String sessionId,
            @Valid @RequestBody SaveAnswersRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "30001") String userId) {
        examDomainService.saveAnswers(sessionId, request, userId);
        return ApiResponse.ok();
    }

    @PostMapping("/sessions/{sessionId}/submit")
    public ApiResponse<Map<String, Object>> submit(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-User-Id", defaultValue = "30001") String userId) {
        return ApiResponse.ok(examDomainService.submit(sessionId, userId));
    }
}

