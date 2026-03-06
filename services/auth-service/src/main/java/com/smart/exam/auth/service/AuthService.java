package com.smart.exam.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Collections;
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
    private static final Duration USER_CACHE_TTL = Duration.ofMinutes(10);
    private static final String LOGIN_DEDUP_PREFIX = "auth:login:dedup:";
    private static final String USER_CACHE_PREFIX = "auth:user:";

    private final JwtUtil jwtUtil;
    private final SysUserMapper sysUserMapper;
    private final RolePermissionReadMapper rolePermissionReadMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean allowLegacyPlainPassword;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Map<String, DemoUser> demoUsers = Map.of(
            "admin", new DemoUser(10001L, "admin", "123456", "ADMIN", "System Admin"),
            "teacher1", new DemoUser(20001L, "teacher1", "123456", "TEACHER", "Teacher One"),
            "stu1", new DemoUser(30001L, "stu1", "123456", "STUDENT", "Student One")
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
                       ObjectMapper objectMapper,
                       @Value("${smart-exam.auth.security.allow-legacy-plain-password:false}")
                       boolean allowLegacyPlainPassword) {
        this.jwtUtil = jwtUtil;
        this.sysUserMapper = sysUserMapper;
        this.rolePermissionReadMapper = rolePermissionReadMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.allowLegacyPlainPassword = allowLegacyPlainPassword;
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
        upgradePasswordHashIfNeeded(user, request.getPassword());
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "User is disabled");
        }
        List<String> permissionCodes = loadPermissionCodes(user.getRole());

        String token = jwtUtil.generateToken(
                String.valueOf(user.getId()),
                user.getRole(),
                Map.of(
                        "username", user.getUsername(),
                        "permissions", permissionCodes
                )
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        payload.put("expiresIn", jwtUtil.getExpirationSeconds());
        payload.put("user", Map.of(
                "id", String.valueOf(user.getId()),
                "username", user.getUsername(),
                "role", user.getRole(),
                "permissions", permissionCodes
        ));
        return payload;
    }

    private SysUserEntity tryCreateDemoUser(LoginRequest request) {
        DemoUser demoUser = demoUsers.get(request.getUsername());
        if (demoUser == null || !demoUser.password().equals(request.getPassword())) {
            return null;
        }

        try {
            SysUserEntity existing = sysUserMapper.selectById(demoUser.id());
            if (existing != null) {
                cacheUser(existing);
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
            evictUserCache(demoUser.username());
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
        SysUserEntity cached = getCachedUser(username);
        if (cached != null) {
            return cached;
        }

        SysUserEntity entity = sysUserMapper.selectOne(
                Wrappers.lambdaQuery(SysUserEntity.class)
                        .eq(SysUserEntity::getUsername, username)
                        .last("limit 1")
        );
        if (entity != null) {
            cacheUser(entity);
        }
        return entity;
    }

    private boolean passwordMatches(String rawPassword, String storedPasswordHash) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(storedPasswordHash)) {
            return false;
        }
        if (isBcryptHash(storedPasswordHash)) {
            try {
                return passwordEncoder.matches(rawPassword, storedPasswordHash);
            } catch (Exception ex) {
                log.warn("Failed to verify bcrypt password", ex);
                return false;
            }
        }
        if (!allowLegacyPlainPassword) {
            log.warn("Rejected legacy plain password login attempt for account");
            return false;
        }
        return rawPassword.equals(storedPasswordHash);
    }

    private void upgradePasswordHashIfNeeded(SysUserEntity user, String rawPassword) {
        if (user == null
                || !StringUtils.hasText(rawPassword)
                || isBcryptHash(user.getPasswordHash())
                || !allowLegacyPlainPassword) {
            return;
        }
        try {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            sysUserMapper.updateById(user);
            cacheUser(user);
        } catch (Exception ex) {
            log.warn("Failed to upgrade password hash, userId={}", user.getId(), ex);
        }
    }

    private boolean isBcryptHash(String value) {
        return StringUtils.hasText(value) && value.startsWith("$2");
    }

    private void evictUserCache(String username) {
        try {
            redisTemplate.delete(USER_CACHE_PREFIX + username);
        } catch (Exception ex) {
            log.warn("Failed to evict user cache", ex);
        }
    }

    private SysUserEntity getCachedUser(String username) {
        try {
            String raw = redisTemplate.opsForValue().get(USER_CACHE_PREFIX + username);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            return objectMapper.readValue(raw, SysUserEntity.class);
        } catch (Exception ex) {
            log.warn("Failed to read cached user, username={}", username, ex);
            return null;
        }
    }

    private void cacheUser(SysUserEntity user) {
        try {
            redisTemplate.opsForValue().set(
                    USER_CACHE_PREFIX + user.getUsername(),
                    objectMapper.writeValueAsString(user),
                    USER_CACHE_TTL
            );
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize user cache", ex);
        } catch (Exception ex) {
            log.warn("Failed to write user cache", ex);
        }
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
        List<String> dbPermissions = Collections.emptyList();
        try {
            dbPermissions = rolePermissionReadMapper.selectPermissionCodesByRoleCode(normalizedRole);
        } catch (Exception ex) {
            log.warn("Failed to load role permissions from DB, role={}", normalizedRole, ex);
        }

        Set<String> merged = new LinkedHashSet<>();
        if (dbPermissions != null) {
            merged.addAll(normalizePermissions(dbPermissions));
        }

        if (merged.isEmpty()) {
            merged.addAll(defaultPermissionsByRole.getOrDefault(normalizedRole, List.of()));
        }
        return new ArrayList<>(merged);
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
