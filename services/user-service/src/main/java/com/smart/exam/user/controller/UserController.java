package com.smart.exam.user.controller;

import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.core.model.ApiResponse;
import com.smart.exam.user.model.UserProfile;
import com.smart.exam.user.service.UserProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
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
            @RequestHeader(value = "X-Role", required = false) String role) {
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        UserProfile profile = userProfileService.findById(userId);
        if (profile == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return ApiResponse.ok(Map.of("id", profile.getId(), "role", role, "profile", profile));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserProfile> detail(@PathVariable String id) {
        UserProfile profile = userProfileService.findById(id);
        if (profile == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return ApiResponse.ok(profile);
    }

    @GetMapping
    public ApiResponse<Collection<UserProfile>> list() {
        return ApiResponse.ok(userProfileService.listAll());
    }
}

