package com.smart.exam.auth.service;

import com.smart.exam.auth.dto.LoginRequest;
import com.smart.exam.auth.entity.SysUserEntity;
import com.smart.exam.auth.mapper.RolePermissionReadMapper;
import com.smart.exam.auth.mapper.SysUserMapper;
import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.security.jwt.JwtProperties;
import com.smart.exam.common.security.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private RolePermissionReadMapper rolePermissionReadMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("01234567890123456789012345678901!");

        authService = new AuthService(
                new JwtUtil(jwtProperties),
                sysUserMapper,
                rolePermissionReadMapper,
                redisTemplate,
                false,
                false
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    @Test
    void loginFailsClosedWhenRolePermissionsCannotBeLoaded() {
        when(sysUserMapper.selectOne(any())).thenReturn(activeTeacher());
        when(rolePermissionReadMapper.selectPermissionCodesByRoleCode("TEACHER"))
                .thenThrow(new IllegalStateException("permission db unavailable"));

        BizException exception = assertThrows(BizException.class, () -> authService.login(loginRequest()));

        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), exception.getCode());
    }

    @Test
    void loginDoesNotFallbackToDefaultPermissionsWhenRolePermissionsAreEmpty() {
        when(sysUserMapper.selectOne(any())).thenReturn(activeTeacher());
        when(rolePermissionReadMapper.selectPermissionCodesByRoleCode("TEACHER")).thenReturn(List.of());

        Map<String, Object> payload = authService.login(loginRequest());
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) payload.get("user");

        assertTrue(((List<?>) user.get("permissions")).isEmpty());
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("teacher001");
        request.setPassword("Passw0rd!");
        return request;
    }

    private SysUserEntity activeTeacher() {
        SysUserEntity user = new SysUserEntity();
        user.setId(21001L);
        user.setUsername("teacher001");
        user.setRealName("Teacher 001");
        user.setRole("TEACHER");
        user.setStatus(1);
        user.setPasswordHash(new BCryptPasswordEncoder().encode("Passw0rd!"));
        return user;
    }
}
