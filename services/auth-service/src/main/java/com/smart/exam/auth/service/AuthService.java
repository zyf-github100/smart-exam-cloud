package com.smart.exam.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.exam.auth.dto.LoginRequest;
import com.smart.exam.auth.entity.SysUserEntity;
import com.smart.exam.auth.mapper.SysUserMapper;
import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.security.jwt.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration LOGIN_DEDUP_TTL = Duration.ofSeconds(3);
    private static final Duration USER_CACHE_TTL = Duration.ofMinutes(10);
    private static final String LOGIN_DEDUP_PREFIX = "auth:login:dedup:";
    private static final String USER_CACHE_PREFIX = "auth:user:";

    private final JwtUtil jwtUtil;
    private final SysUserMapper sysUserMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, DemoUser> demoUsers = Map.of(
            "admin", new DemoUser(10001L, "admin", "123456", "ADMIN", "System Admin"),
            "teacher1", new DemoUser(20001L, "teacher1", "123456", "TEACHER", "Teacher One"),
            "stu1", new DemoUser(30001L, "stu1", "123456", "STUDENT", "Student One")
    );

    public AuthService(JwtUtil jwtUtil,
                       SysUserMapper sysUserMapper,
                       StringRedisTemplate redisTemplate,
                       ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.sysUserMapper = sysUserMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> login(LoginRequest request) {
        protectDuplicateLogin(request);

        SysUserEntity user = findByUsername(request.getUsername());
        if (user == null) {
            user = tryCreateDemoUser(request);
        }

        if (user == null || !passwordMatches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Invalid username or password");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "User is disabled");
        }

        String token = jwtUtil.generateToken(
                String.valueOf(user.getId()),
                user.getRole(),
                Map.of("username", user.getUsername())
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        payload.put("expiresIn", jwtUtil.getExpirationSeconds());
        payload.put("user", Map.of(
                "id", String.valueOf(user.getId()),
                "username", user.getUsername(),
                "role", user.getRole()
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
            entity.setPasswordHash(demoUser.password());
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
        return StringUtils.hasText(rawPassword) && rawPassword.equals(storedPasswordHash);
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

    private record DemoUser(Long id, String username, String password, String role, String realName) {
    }
}
