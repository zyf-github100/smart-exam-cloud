package com.smart.exam.user.controller;

import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.core.model.ApiResponse;
import com.smart.exam.common.web.security.PermissionCodes;
import com.smart.exam.common.web.security.RoleGuard;
import com.smart.exam.user.model.UserProfile;
import com.smart.exam.user.service.UserProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String currentUserId = RoleGuard.requireUserId(userId);
        String currentRole = RoleGuard.requireRole(role, "ADMIN", "TEACHER", "STUDENT");
        RoleGuard.requirePermission(currentRole, permissions, PermissionCodes.USER_SELF_VIEW);
        UserProfile profile = userProfileService.findById(currentUserId);
        if (profile == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "User not found");
        }
        return ApiResponse.ok(Map.of("id", profile.getId(), "role", currentRole, "profile", profile));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserProfile> detail(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String currentUserId = RoleGuard.requireUserId(userId);
        String currentRole = RoleGuard.requireRole(role, "ADMIN", "TEACHER");
        RoleGuard.requirePermission(currentRole, permissions, PermissionCodes.USER_PROFILE_VIEW);
        UserProfile profile = userProfileService.findById(id);
        if (profile == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "User not found");
        }
        if (!canViewProfile(currentUserId, currentRole, profile)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Access denied");
        }
        return ApiResponse.ok(profile);
    }

    @GetMapping
    public ApiResponse<Collection<UserProfile>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String currentRole = requireTeacherOrAdmin(userId, role, permissions, PermissionCodes.USER_LIST_VIEW);
        return ApiResponse.ok(userProfileService.listVisibleForRole(currentRole));
    }

    private String requireTeacherOrAdmin(String userId, String role, String permissions, String requiredPermission) {
        RoleGuard.requireUserId(userId);
        String currentRole = RoleGuard.requireRole(role, "ADMIN", "TEACHER");
        RoleGuard.requirePermission(currentRole, permissions, requiredPermission);
        return currentRole;
    }

    private boolean canViewProfile(String currentUserId, String currentRole, UserProfile profile) {
        String normalizedRole = normalizeRole(currentRole);
        if ("ADMIN".equals(normalizedRole)) {
            return true;
        }
        if ("TEACHER".equals(normalizedRole)) {
            return currentUserId.equals(profile.getId()) || "STUDENT".equals(normalizeRole(profile.getRole()));
        }
        return currentUserId.equals(profile.getId());
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }
}
