package com.smart.exam.question.controller;

import com.smart.exam.common.core.model.ApiResponse;
import com.smart.exam.question.dto.CreatePaperRequest;
import com.smart.exam.question.dto.CreateQuestionRequest;
import com.smart.exam.question.model.Paper;
import com.smart.exam.question.model.Question;
import com.smart.exam.question.service.QuestionDomainService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("/api/v1")
public class QuestionController {

    private final QuestionDomainService questionDomainService;

    public QuestionController(QuestionDomainService questionDomainService) {
        this.questionDomainService = questionDomainService;
    }

    @PostMapping("/questions")
    public ApiResponse<Question> createQuestion(
            @Valid @RequestBody CreateQuestionRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "20001") String userId) {
        return ApiResponse.ok(questionDomainService.createQuestion(request, userId));
    }

    @GetMapping("/questions")
    public ApiResponse<Collection<Question>> listQuestions() {
        return ApiResponse.ok(questionDomainService.listQuestions());
    }

    @GetMapping("/questions/{questionId}")
    public ApiResponse<Question> getQuestion(@PathVariable String questionId) {
        return ApiResponse.ok(questionDomainService.findQuestion(questionId));
    }

    @PostMapping("/papers")
    public ApiResponse<Paper> createPaper(
            @Valid @RequestBody CreatePaperRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "20001") String userId) {
        return ApiResponse.ok(questionDomainService.createPaper(request, userId));
    }

    @GetMapping("/papers/{paperId}")
    public ApiResponse<Paper> getPaper(@PathVariable String paperId) {
        return ApiResponse.ok(questionDomainService.findPaper(paperId));
    }
}

