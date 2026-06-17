package com.smart.exam.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.exam.admin.dto.ResetPasswordRequest;
import com.smart.exam.admin.dto.UpdateRolePermissionsRequest;
import com.smart.exam.admin.dto.UpdateUserStatusRequest;
import com.smart.exam.admin.entity.SysPermissionEntity;
import com.smart.exam.admin.entity.SysRoleEntity;
import com.smart.exam.admin.entity.SysUserEntity;
import com.smart.exam.admin.mapper.AdminStatsMapper;
import com.smart.exam.admin.mapper.SysAuditLogMapper;
import com.smart.exam.admin.mapper.SysConfigMapper;
import com.smart.exam.admin.mapper.SysPermissionMapper;
import com.smart.exam.admin.mapper.SysRoleMapper;
import com.smart.exam.admin.mapper.SysRolePermissionMapper;
import com.smart.exam.admin.mapper.SysUserMapper;
import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.web.audit.AuditActions;
import com.smart.exam.common.web.audit.AuditTargetTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private SysPermissionMapper permissionMapper;

    @Mock
    private SysRolePermissionMapper rolePermissionMapper;

    @Mock
    private SysConfigMapper configMapper;

    @Mock
    private SysAuditLogMapper auditLogMapper;

    @Mock
    private AdminStatsMapper statsMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AdminAuditService auditService;

    private AdminService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        service = new AdminService(
                userMapper,
                roleMapper,
                permissionMapper,
                rolePermissionMapper,
                configMapper,
                auditLogMapper,
                statsMapper,
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                auditService
        );
    }

    @Test
    void resetPasswordRejectsWeakPasswordBeforeReadingUser() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setNewPassword("weakpass");

        BizException exception = assertThrows(
                BizException.class,
                () -> service.resetPassword("100", request, "1", "ADMIN", "127.0.0.1", "JUnit")
        );

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("Password must contain"));
        verifyNoInteractions(userMapper, auditService);
    }

    @Test
    void updateRolePermissionsRejectsUnknownPermissionCodes() {
        when(roleMapper.selectOne(any())).thenReturn(activeRole("TEACHER"));
        when(permissionMapper.selectList(any())).thenReturn(List.of(permission("QUESTION_READ", 1)));

        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        request.setPermissionCodes(List.of("question_read", "question_write"));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.updateRolePermissions("teacher", request, "1", "ADMIN", "127.0.0.1", "JUnit")
        );

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("QUESTION_WRITE"));
        verify(rolePermissionMapper, never()).delete(any());
        verify(rolePermissionMapper, never()).insert(any(com.smart.exam.admin.entity.SysRolePermissionEntity.class));
        verifyNoInteractions(auditService);
    }

    @Test
    void updateUserStatusEvictsOverviewCacheAndWritesAudit() {
        when(userMapper.selectById(100L)).thenReturn(user(100L, 1));

        UpdateUserStatusRequest request = new UpdateUserStatusRequest();
        request.setStatus(0);
        request.setReason(" lock user ");

        service.updateUserStatus("100", request, "9001", "ADMIN", "127.0.0.1", "JUnit");

        ArgumentCaptor<SysUserEntity> userCaptor = ArgumentCaptor.forClass(SysUserEntity.class);
        verify(userMapper).updateById(userCaptor.capture());
        SysUserEntity updated = userCaptor.getValue();
        assertEquals(0, updated.getStatus());
        assertNotNull(updated.getUpdatedAt());

        verify(redisTemplate).delete("admin:overview");

        ArgumentCaptor<Object> detailCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.eq(AuditActions.USER_STATUS_UPDATED),
                org.mockito.ArgumentMatchers.eq(AuditTargetTypes.SYS_USER),
                org.mockito.ArgumentMatchers.eq("100"),
                detailCaptor.capture(),
                anyString(),
                anyString()
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) detailCaptor.getValue();
        assertEquals(1, detail.get("beforeStatus"));
        assertEquals(0, detail.get("afterStatus"));
        assertEquals("lock user", detail.get("reason"));
    }

    private SysRoleEntity activeRole(String roleCode) {
        SysRoleEntity role = new SysRoleEntity();
        role.setRoleCode(roleCode);
        role.setStatus(1);
        return role;
    }

    private SysPermissionEntity permission(String code, int status) {
        SysPermissionEntity permission = new SysPermissionEntity();
        permission.setPermissionCode(code);
        permission.setStatus(status);
        return permission;
    }

    private SysUserEntity user(Long id, Integer status) {
        SysUserEntity user = new SysUserEntity();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setStatus(status);
        user.setRole("STUDENT");
        return user;
    }
}
