package com.smart.exam.exam.controller;

import com.smart.exam.common.core.model.ApiResponse;
import com.smart.exam.common.web.security.PermissionCodes;
import com.smart.exam.common.web.security.RoleGuard;
import com.smart.exam.exam.dto.CreateExamRequest;
import com.smart.exam.exam.dto.ReportAntiCheatEventRequest;
import com.smart.exam.exam.dto.SaveAnswersRequest;
import com.smart.exam.exam.model.AssignedExam;
import com.smart.exam.exam.model.Exam;
import com.smart.exam.exam.model.ExamPaper;
import com.smart.exam.exam.model.SessionAnswer;
import com.smart.exam.exam.service.ExamDomainService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String operatorId = requireTeacherOrAdmin(userId, role, permissions, PermissionCodes.EXAM_CREATE);
        return ApiResponse.ok(examDomainService.createExam(request, operatorId, role));
    }

    @PostMapping("/exams/{examId}/start")
    public ApiResponse<Map<String, Object>> startExam(
            @PathVariable("examId") String examId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            HttpServletRequest servletRequest) {
        String studentId = requireStudentOrAdmin(userId, role, permissions, PermissionCodes.EXAM_SESSION_START);
        String ip = servletRequest.getRemoteAddr();
        return ApiResponse.ok(examDomainService.startExam(examId, studentId, role, ip));
    }

    @GetMapping({"/students/me/exams", "/exams/students/me"})
    public ApiResponse<List<AssignedExam>> listMyExams(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String studentId = requireStudentOrAdmin(userId, role, permissions, PermissionCodes.EXAM_SESSION_START);
        return ApiResponse.ok(examDomainService.listAssignedExams(studentId, role));
    }

    @GetMapping("/exams/teachers/me")
    public ApiResponse<List<Exam>> listMyPublishedExams(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String teacherId = requireTeacherOrAdmin(userId, role, permissions, PermissionCodes.EXAM_CREATE);
        return ApiResponse.ok(examDomainService.listPublishedExams(teacherId, role));
    }

    @PutMapping("/sessions/{sessionId}/answers")
    public ApiResponse<Void> saveAnswers(
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody SaveAnswersRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String studentId = requireStudentOrAdmin(userId, role, permissions, PermissionCodes.EXAM_ANSWER_SAVE);
        examDomainService.saveAnswers(sessionId, request, studentId);
        return ApiResponse.ok();
    }

    @GetMapping("/sessions/{sessionId}/paper")
    public ApiResponse<ExamPaper> getSessionPaper(
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String studentId = requireStudentOrAdmin(userId, role, permissions, PermissionCodes.EXAM_ANSWER_SAVE);
        return ApiResponse.ok(examDomainService.getSessionPaper(sessionId, studentId));
    }

    @GetMapping("/sessions/{sessionId}/answers")
    public ApiResponse<List<SessionAnswer>> getSessionAnswers(
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String studentId = requireStudentOrAdmin(userId, role, permissions, PermissionCodes.EXAM_ANSWER_SAVE);
        return ApiResponse.ok(examDomainService.listSessionAnswers(sessionId, studentId));
    }

    @PostMapping("/sessions/{sessionId}/submit")
    public ApiResponse<Map<String, Object>> submit(
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String studentId = requireStudentOrAdmin(userId, role, permissions, PermissionCodes.EXAM_SESSION_SUBMIT);
        return ApiResponse.ok(examDomainService.submit(sessionId, studentId));
    }

    @PostMapping("/sessions/{sessionId}/anti-cheat/events")
    public ApiResponse<Map<String, Object>> reportAntiCheatEvent(
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody ReportAntiCheatEventRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            HttpServletRequest servletRequest) {
        String studentId = requireStudentOrAdmin(userId, role, permissions, PermissionCodes.EXAM_ANTI_CHEAT_EVENT_REPORT);
        String ip = servletRequest == null ? null : servletRequest.getRemoteAddr();
        return ApiResponse.ok(examDomainService.reportAntiCheatEvent(sessionId, studentId, ip, request));
    }

    @GetMapping("/sessions/{sessionId}/anti-cheat/risk")
    public ApiResponse<Map<String, Object>> getSessionRisk(
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String operatorId = requireTeacherOrAdmin(userId, role, permissions, PermissionCodes.EXAM_ANTI_CHEAT_RISK_VIEW);
        return ApiResponse.ok(examDomainService.getSessionRisk(sessionId, operatorId, role));
    }

    @GetMapping("/exams/{examId}/anti-cheat/risks")
    public ApiResponse<Map<String, Object>> listExamRisks(
            @PathVariable("examId") String examId,
            @RequestParam(name = "riskLevel", required = false) String riskLevel,
            @RequestParam(name = "page", required = false) Long page,
            @RequestParam(name = "size", required = false) Long size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String operatorId = requireTeacherOrAdmin(userId, role, permissions, PermissionCodes.EXAM_ANTI_CHEAT_RISK_VIEW);
        return ApiResponse.ok(examDomainService.listExamRisks(examId, riskLevel, page, size, operatorId, role));
    }

    private String requireTeacherOrAdmin(String userId, String role, String permissions, String requiredPermission) {
        String safeUserId = RoleGuard.requireUserId(userId);
        RoleGuard.requireRole(role, "ADMIN", "TEACHER");
        RoleGuard.requirePermission(role, permissions, requiredPermission);
        return safeUserId;
    }

    private String requireStudentOrAdmin(String userId, String role, String permissions, String requiredPermission) {
        String safeUserId = RoleGuard.requireUserId(userId);
        RoleGuard.requireRole(role, "ADMIN", "STUDENT");
        RoleGuard.requirePermission(role, permissions, requiredPermission);
        return safeUserId;
    }
}
