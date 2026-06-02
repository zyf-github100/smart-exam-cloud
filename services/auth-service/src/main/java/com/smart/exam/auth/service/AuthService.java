package com.smart.exam.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smart.exam.auth.dto.LoginRequest;
import com.smart.exam.auth.entity.SysUserEntity;
import com.smart.exam.auth.mapper.RolePermissionReadMapper;
import com.smart.exam.auth.mapper.SysUserMapper;
import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.security.jwt.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration LOGIN_DEDUP_TTL = Duration.ofSeconds(3);
    private static final String LOGIN_DEDUP_PREFIX = "auth:login:dedup:";

    private final JwtUtil jwtUtil;
    private final SysUserMapper sysUserMapper;
    private final RolePermissionReadMapper rolePermissionReadMapper;
    private final StringRedisTemplate redisTemplate;
    private final boolean bootstrapDemoUsers;
    private final boolean defaultPermissionsEnabled;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Map<String, DemoUser> demoUsers = Map.of(
            "admin", new DemoUser(10001L, "admin", "123456", "ADMIN", "System Admin"),
            "teacher001", new DemoUser(21001L, "teacher001", "123456", "TEACHER", "Teacher 001"),
            "student001", new DemoUser(31001L, "student001", "123456", "STUDENT", "Student 001")
    );
    private final Map<String, List<String>> defaultPermissionsByRole = Map.of(
            "ADMIN", List.of(
                    "ADMIN_PLATFORM_ACCESS",
                    "ADMIN_OVERVIEW_READ",
                    "ADMIN_USER_VIEW",
                    "ADMIN_USER_STATUS_UPDATE",
                    "ADMIN_USER_ROLE_UPDATE",
                    "ADMIN_USER_PASSWORD_RESET",
                    "ADMIN_ROLE_PERMISSION_ASSIGN",
                    "ADMIN_CONFIG_READ",
                    "ADMIN_CONFIG_WRITE",
                    "ADMIN_AUDIT_READ",
                    "EXAM_CREATE",
                    "EXAM_SESSION_START",
                    "EXAM_ANSWER_SAVE",
                    "EXAM_SESSION_SUBMIT",
                    "EXAM_ANTI_CHEAT_EVENT_REPORT",
                    "EXAM_ANTI_CHEAT_RISK_VIEW",
                    "GRADING_TASK_VIEW",
                    "GRADING_MANUAL_SCORE",
                    "QUESTION_CREATE",
                    "QUESTION_LIST",
                    "QUESTION_DETAIL",
                    "PAPER_CREATE",
                    "PAPER_DETAIL",
                    "REPORT_SCORE_DISTRIBUTION_VIEW",
                    "REPORT_QUESTION_ACCURACY_VIEW",
                    "USER_SELF_VIEW",
                    "USER_PROFILE_VIEW",
                    "USER_LIST_VIEW"
            ),
            "TEACHER", List.of(
                    "EXAM_CREATE",
                    "EXAM_ANTI_CHEAT_RISK_VIEW",
                    "GRADING_TASK_VIEW",
                    "GRADING_MANUAL_SCORE",
                    "QUESTION_CREATE",
                    "QUESTION_LIST",
                    "QUESTION_DETAIL",
                    "PAPER_CREATE",
                    "PAPER_DETAIL",
                    "REPORT_SCORE_DISTRIBUTION_VIEW",
                    "REPORT_QUESTION_ACCURACY_VIEW",
                    "USER_SELF_VIEW",
                    "USER_PROFILE_VIEW",
                    "USER_LIST_VIEW"
            ),
            "STUDENT", List.of(
                    "EXAM_SESSION_START",
                    "EXAM_ANSWER_SAVE",
                    "EXAM_SESSION_SUBMIT",
                    "EXAM_ANTI_CHEAT_EVENT_REPORT",
                    "STUDENT_RESULT_VIEW",
                    "USER_SELF_VIEW"
            )
    );

    public AuthService(JwtUtil jwtUtil,
                       SysUserMapper sysUserMapper,
                       RolePermissionReadMapper rolePermissionReadMapper,
                       StringRedisTemplate redisTemplate,
                       @Value("${smart-exam.auth.bootstrap-demo-users:false}") boolean bootstrapDemoUsers,
                       @Value("${smart-exam.auth.security.default-permissions-enabled:false}")
                       boolean defaultPermissionsEnabled) {
        this.jwtUtil = jwtUtil;
        this.sysUserMapper = sysUserMapper;
        this.rolePermissionReadMapper = rolePermissionReadMapper;
        this.redisTemplate = redisTemplate;
        this.bootstrapDemoUsers = bootstrapDemoUsers;
        this.defaultPermissionsEnabled = defaultPermissionsEnabled;
    }

    public Map<String, Object> login(LoginRequest request) {
        protectDuplicateLogin(request);

        SysUserEntity user = findByUsername(request.getUsername());
        if (user == null) {
            user = tryCreateDemoUser(request);
        }

        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Invalid username or password");
        }

        boolean passwordMatched = passwordMatches(request.getPassword(), user.getPasswordHash());
        if (!passwordMatched) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Invalid username or password");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "User is disabled");
        }
        List<String> permissionCodes = loadPermissionCodes(user.getRole());
        String userId = String.valueOf(user.getId());
        String username = user.getUsername() == null ? "" : user.getUsername();
        String role = user.getRole() == null ? "" : user.getRole();
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("username", username);
        extraClaims.put("permissions", permissionCodes);

        String token = jwtUtil.generateToken(
                userId,
                role,
                extraClaims
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        payload.put("expiresIn", jwtUtil.getExpirationSeconds());
        Map<String, Object> userPayload = new HashMap<>();
        userPayload.put("id", userId);
        userPayload.put("username", username);
        userPayload.put("role", role);
        userPayload.put("permissions", permissionCodes);
        payload.put("user", userPayload);
        return payload;
    }

    private SysUserEntity tryCreateDemoUser(LoginRequest request) {
        if (!bootstrapDemoUsers) {
            return null;
        }
        DemoUser demoUser = demoUsers.get(request.getUsername());
        if (demoUser == null || !demoUser.password().equals(request.getPassword())) {
            return null;
        }

        try {
            SysUserEntity existing = sysUserMapper.selectById(demoUser.id());
            if (existing != null) {
                return existing;
            }

            SysUserEntity entity = new SysUserEntity();
            entity.setId(demoUser.id());
            entity.setUsername(demoUser.username());
            entity.setPasswordHash(passwordEncoder.encode(demoUser.password()));
            entity.setRealName(demoUser.realName());
            entity.setRole(demoUser.role());
            entity.setStatus(1);
            sysUserMapper.insert(entity);
            return entity;
        } catch (Exception ex) {
            log.warn("Failed to create demo user in DB, username={}", demoUser.username(), ex);
            return findByUsername(demoUser.username());
        }
    }

    private void protectDuplicateLogin(LoginRequest request) {
        String key = LOGIN_DEDUP_PREFIX + request.getUsername() + ":" + sha256(request.getPassword());
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", LOGIN_DEDUP_TTL);
            if (Boolean.FALSE.equals(ok)) {
                throw new BizException(ErrorCode.CONFLICT, "Duplicate login request");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to execute login dedup check", ex);
        }
    }

    private SysUserEntity findByUsername(String username) {
        return sysUserMapper.selectOne(
                Wrappers.lambdaQuery(SysUserEntity.class)
                        .eq(SysUserEntity::getUsername, username)
                        .last("limit 1")
        );
    }

    private boolean passwordMatches(String rawPassword, String storedPasswordHash) {
        if (!StringUtils.hasText(rawPassword)
                || !StringUtils.hasText(storedPasswordHash)
                || !isBcryptHash(storedPasswordHash)) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawPassword, storedPasswordHash);
        } catch (Exception ex) {
            log.warn("Failed to verify bcrypt password", ex);
            return false;
        }
    }

    private boolean isBcryptHash(String value) {
        return StringUtils.hasText(value) && value.startsWith("$2");
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "SHA-256 unavailable");
        }
    }

    private List<String> loadPermissionCodes(String role) {
        String normalizedRole = normalizeRole(role);
        List<String> dbPermissions;
        try {
            dbPermissions = rolePermissionReadMapper.selectPermissionCodesByRoleCode(normalizedRole);
        } catch (Exception ex) {
            if (defaultPermissionsEnabled) {
                log.warn("Failed to load role permissions from DB, fallback to default permissions, role={}",
                        normalizedRole,
                        ex);
                return defaultPermissionCodes(normalizedRole);
            }
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to load role permissions");
        }

        Set<String> merged = new LinkedHashSet<>();
        if (dbPermissions != null) {
            merged.addAll(normalizePermissions(dbPermissions));
        }

        if (merged.isEmpty() && defaultPermissionsEnabled) {
            return defaultPermissionCodes(normalizedRole);
        }
        return new ArrayList<>(merged);
    }

    private List<String> defaultPermissionCodes(String normalizedRole) {
        return new ArrayList<>(defaultPermissionsByRole.getOrDefault(normalizedRole, List.of()));
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "";
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> normalizePermissions(List<String> permissions) {
        if (permissions == null) {
            return List.of();
        }
        return permissions.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
    }

    private record DemoUser(Long id, String username, String password, String role, String realName) {
    }
}
