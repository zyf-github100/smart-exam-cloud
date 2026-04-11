package com.smart.exam.admin.controller;

import com.smart.exam.admin.dto.ResetPasswordRequest;
import com.smart.exam.admin.dto.UpdateRolePermissionsRequest;
import com.smart.exam.admin.dto.UpdateUserRoleRequest;
import com.smart.exam.admin.dto.UpdateUserStatusRequest;
import com.smart.exam.admin.dto.UpsertConfigRequest;
import com.smart.exam.admin.service.AdminService;
import com.smart.exam.common.core.model.ApiResponse;
import com.smart.exam.common.web.security.RoleGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role) {
        requireAdmin(userId, role);
        return ApiResponse.ok(adminService.getOverview());
    }

    @GetMapping("/users")
    public ApiResponse<Map<String, Object>> listUsers(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "roleCode", required = false) String roleCode,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "page", required = false) Long page,
            @RequestParam(name = "size", required = false) Long size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role) {
        requireAdmin(userId, role);
        return ApiResponse.ok(adminService.listUsers(keyword, roleCode, status, page, size));
    }

    @PutMapping("/users/{userId}/status")
    public ApiResponse<Void> updateUserStatus(
            @PathVariable("userId") String userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String operatorId,
            @RequestHeader(value = "X-Role", required = false) String operatorRole,
            HttpServletRequest servletRequest) {
        OperatorContext context = buildAdminContext(operatorId, operatorRole, servletRequest);
        adminService.updateUserStatus(
                userId,
                request,
                context.userId(),
                context.role(),
                context.ip(),
                context.userAgent()
        );
        return ApiResponse.ok();
    }

    @PutMapping("/users/{userId}/role")
    public ApiResponse<Void> updateUserRole(
            @PathVariable("userId") String userId,
            @Valid @RequestBody UpdateUserRoleRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String operatorId,
            @RequestHeader(value = "X-Role", required = false) String operatorRole,
            HttpServletRequest servletRequest) {
        OperatorContext context = buildAdminContext(operatorId, operatorRole, servletRequest);
        adminService.updateUserRole(
                userId,
                request,
                context.userId(),
                context.role(),
                context.ip(),
                context.userAgent()
        );
        return ApiResponse.ok();
    }

    @PutMapping("/users/{userId}/password/reset")
    public ApiResponse<Void> resetPassword(
            @PathVariable("userId") String userId,
            @Valid @RequestBody ResetPasswordRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String operatorId,
            @RequestHeader(value = "X-Role", required = false) String operatorRole,
            HttpServletRequest servletRequest) {
        OperatorContext context = buildAdminContext(operatorId, operatorRole, servletRequest);
        adminService.resetPassword(
                userId,
                request,
                context.userId(),
                context.role(),
                context.ip(),
                context.userAgent()
        );
        return ApiResponse.ok();
    }

    @GetMapping("/roles")
    public ApiResponse<List<Map<String, Object>>> listRoles(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role) {
        requireAdmin(userId, role);
        return ApiResponse.ok(adminService.listRoles());
    }

    @GetMapping("/permissions")
    public ApiResponse<List<Map<String, Object>>> listPermissions(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role) {
        requireAdmin(userId, role);
        return ApiResponse.ok(adminService.listPermissions());
    }

    @PutMapping("/roles/{roleCode}/permissions")
    public ApiResponse<Void> updateRolePermissions(
            @PathVariable("roleCode") String roleCode,
            @Valid @RequestBody UpdateRolePermissionsRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String operatorId,
            @RequestHeader(value = "X-Role", required = false) String operatorRole,
            HttpServletRequest servletRequest) {
        OperatorContext context = buildAdminContext(operatorId, operatorRole, servletRequest);
        adminService.updateRolePermissions(
                roleCode,
                request,
                context.userId(),
                context.role(),
                context.ip(),
                context.userAgent()
        );
        return ApiResponse.ok();
    }

    @GetMapping("/configs")
    public ApiResponse<Map<String, Object>> listConfigs(
            @RequestParam(name = "groupKey", required = false) String groupKey,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role) {
        requireAdmin(userId, role);
        return ApiResponse.ok(adminService.listConfigs(groupKey, keyword));
    }

    @PutMapping("/configs/{configKey}")
    public ApiResponse<Void> upsertConfig(
            @PathVariable("configKey") String configKey,
            @Valid @RequestBody UpsertConfigRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String operatorId,
            @RequestHeader(value = "X-Role", required = false) String operatorRole,
            HttpServletRequest servletRequest) {
        OperatorContext context = buildAdminContext(operatorId, operatorRole, servletRequest);
        adminService.upsertConfig(
                configKey,
                request,
                context.userId(),
                context.role(),
                context.ip(),
                context.userAgent()
        );
        return ApiResponse.ok();
    }

    @GetMapping("/audits")
    public ApiResponse<Map<String, Object>> listAudits(
            @RequestParam(name = "serviceName", required = false) String serviceName,
            @RequestParam(name = "moduleKey", required = false) String moduleKey,
            @RequestParam(name = "operatorId", required = false) String operatorId,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "targetType", required = false) String targetType,
            @RequestParam(name = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(name = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(name = "page", required = false) Long page,
            @RequestParam(name = "size", required = false) Long size,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role) {
        requireAdmin(userId, role);
        return ApiResponse.ok(adminService.listAuditLogs(serviceName, moduleKey, operatorId, action, targetType, startTime, endTime, page, size));
    }

    private void requireAdmin(String userId, String role) {
        RoleGuard.requireUserId(userId);
        RoleGuard.requireRole(role, "ADMIN");
    }

    private OperatorContext buildAdminContext(String userId, String role, HttpServletRequest servletRequest) {
        String operatorId = RoleGuard.requireUserId(userId);
        String operatorRole = RoleGuard.requireRole(role, "ADMIN");
        String ip = com.smart.exam.common.web.audit.AuditHttpUtils.extractClientIp(servletRequest);
        String userAgent = com.smart.exam.common.web.audit.AuditHttpUtils.extractUserAgent(servletRequest);
        return new OperatorContext(operatorId, operatorRole, ip, userAgent);
    }

    private record OperatorContext(String userId, String role, String ip, String userAgent) {
    }
}
