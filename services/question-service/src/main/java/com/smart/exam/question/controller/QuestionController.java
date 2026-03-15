package com.smart.exam.question.controller;

import com.smart.exam.common.core.model.ApiResponse;
import com.smart.exam.common.web.security.PermissionCodes;
import com.smart.exam.common.web.security.RoleGuard;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String operatorId = requireTeacherOrAdmin(userId, role, permissions, PermissionCodes.QUESTION_CREATE);
        return ApiResponse.ok(questionDomainService.createQuestion(request, operatorId));
    }

    @GetMapping("/questions")
    public ApiResponse<Map<String, Object>> listQuestions(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "page", required = false) Long page,
            @RequestParam(name = "size", required = false) Long size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String operatorId = requireTeacherOrAdmin(userId, role, permissions, PermissionCodes.QUESTION_LIST);
        return ApiResponse.ok(questionDomainService.listQuestions(operatorId, role, keyword, type, page, size));
    }

    @GetMapping("/questions/{questionId}")
    public ApiResponse<Question> getQuestion(
            @PathVariable("questionId") String questionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String operatorId = requireTeacherOrAdmin(userId, role, permissions, PermissionCodes.QUESTION_DETAIL);
        return ApiResponse.ok(questionDomainService.findQuestion(questionId, operatorId, role));
    }

    @PostMapping("/papers")
    public ApiResponse<Paper> createPaper(
            @Valid @RequestBody CreatePaperRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String operatorId = requireTeacherOrAdmin(userId, role, permissions, PermissionCodes.PAPER_CREATE);
        return ApiResponse.ok(questionDomainService.createPaper(request, operatorId, role));
    }

    @GetMapping("/papers")
    public ApiResponse<Map<String, Object>> listPapers(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", required = false) Long page,
            @RequestParam(name = "size", required = false) Long size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String operatorId = requireTeacherOrAdmin(userId, role, permissions, PermissionCodes.PAPER_DETAIL);
        return ApiResponse.ok(questionDomainService.listPapers(operatorId, role, keyword, page, size));
    }

    @GetMapping("/papers/{paperId}")
    public ApiResponse<Paper> getPaper(
            @PathVariable("paperId") String paperId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String operatorId = requireTeacherOrAdmin(userId, role, permissions, PermissionCodes.PAPER_DETAIL);
        return ApiResponse.ok(questionDomainService.findPaper(paperId, operatorId, role));
    }

    private String requireTeacherOrAdmin(String userId, String role, String permissions, String requiredPermission) {
        String safeUserId = RoleGuard.requireUserId(userId);
        RoleGuard.requireRole(role, "ADMIN", "TEACHER");
        RoleGuard.requirePermission(role, permissions, requiredPermission);
        return safeUserId;
    }
}
