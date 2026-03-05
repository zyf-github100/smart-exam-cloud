package com.smart.exam.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.exam.admin.dto.ResetPasswordRequest;
import com.smart.exam.admin.dto.UpdateRolePermissionsRequest;
import com.smart.exam.admin.dto.UpdateUserRoleRequest;
import com.smart.exam.admin.dto.UpdateUserStatusRequest;
import com.smart.exam.admin.dto.UpsertConfigRequest;
import com.smart.exam.admin.entity.SysAuditLogEntity;
import com.smart.exam.admin.entity.SysConfigEntity;
import com.smart.exam.admin.entity.SysPermissionEntity;
import com.smart.exam.admin.entity.SysRoleEntity;
import com.smart.exam.admin.entity.SysRolePermissionEntity;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final Duration OVERVIEW_CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration ROLE_PERMISSION_CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration MUTATION_DEDUP_TTL = Duration.ofSeconds(5);
    private static final long MAX_PAGE_SIZE = 100L;
    private static final Pattern STRONG_PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])\\S{8,64}$");
    private static final String OVERVIEW_CACHE_KEY = "admin:overview";
    private static final String ROLES_CACHE_KEY = "admin:roles";
    private static final String PERMISSIONS_CACHE_KEY = "admin:permissions";
    private static final String MUTATION_DEDUP_PREFIX = "admin:mutation:dedup:";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysConfigMapper configMapper;
    private final SysAuditLogMapper auditLogMapper;
    private final AdminStatsMapper statsMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AdminAuditService auditService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminService(SysUserMapper userMapper,
                        SysRoleMapper roleMapper,
                        SysPermissionMapper permissionMapper,
                        SysRolePermissionMapper rolePermissionMapper,
                        SysConfigMapper configMapper,
                        SysAuditLogMapper auditLogMapper,
                        AdminStatsMapper statsMapper,
                        StringRedisTemplate redisTemplate,
                        ObjectMapper objectMapper,
                        AdminAuditService auditService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.configMapper = configMapper;
        this.auditLogMapper = auditLogMapper;
        this.statsMapper = statsMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    public Map<String, Object> getOverview() {
        Map<String, Object> cached = getCache(OVERVIEW_CACHE_KEY, new TypeReference<Map<String, Object>>() {
        });
        if (cached != null) {
            return cached;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalUsers", safeCount(statsMapper.countUsers()));
        payload.put("enabledUsers", safeCount(statsMapper.countEnabledUsers()));
        payload.put("disabledUsers", safeCount(statsMapper.countDisabledUsers()));
        payload.put("totalExams", safeCount(statsMapper.countExams()));
        payload.put("runningExams", safeCount(statsMapper.countRunningExams()));
        payload.put("manualRequiredTasks", safeCount(statsMapper.countManualRequiredTasks()));
        payload.put("publishedScores", safeCount(statsMapper.countPublishedScores()));
        payload.put("operationsInLast24Hours", safeCount(statsMapper.countOperationsInLast24Hours()));
        payload.put("generatedAt", LocalDateTime.now());

        putCache(OVERVIEW_CACHE_KEY, payload, OVERVIEW_CACHE_TTL);
        return payload;
    }

    public Map<String, Object> listUsers(String keyword, String role, Integer status, Long page, Long size) {
        long safePage = normalizePage(page);
        long safeSize = normalizeSize(size);
        long offset = (safePage - 1) * safeSize;

        Long total = userMapper.selectCount(buildUserQuery(keyword, role, status, false, 0, 0));
        List<Map<String, Object>> records = List.of();
        if (total != null && total > 0) {
            List<SysUserEntity> entities = userMapper.selectList(buildUserQuery(keyword, role, status, true, offset, safeSize));
            records = entities.stream().map(this::toUserPayload).toList();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", safePage);
        payload.put("size", safeSize);
        payload.put("total", total == null ? 0L : total);
        payload.put("records", records);
        return payload;
    }

    @Transactional
    public void updateUserStatus(String userId,
                                 UpdateUserStatusRequest request,
                                 String operatorId,
                                 String operatorRole,
                                 String ip,
                                 String userAgent) {
        guardMutationDedup(operatorId, "user-status", userId + ":" + request.getStatus());

        SysUserEntity user = requireUser(userId);
        Integer beforeStatus = user.getStatus();
        user.setStatus(request.getStatus());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        evictOverviewCache();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("beforeStatus", beforeStatus);
        detail.put("afterStatus", request.getStatus());
        if (StringUtils.hasText(request.getReason())) {
            detail.put("reason", request.getReason().trim());
        }
        auditService.record(operatorId, operatorRole, "USER_STATUS_UPDATED", "SYS_USER", userId, detail, ip, userAgent);
    }

    @Transactional
    public void updateUserRole(String userId,
                               UpdateUserRoleRequest request,
                               String operatorId,
                               String operatorRole,
                               String ip,
                               String userAgent) {
        String normalizedRoleCode = normalizeCode(request.getRoleCode());
        guardMutationDedup(operatorId, "user-role", userId + ":" + normalizedRoleCode);

        requireRole(normalizedRoleCode);
        SysUserEntity user = requireUser(userId);
        String beforeRole = user.getRole();
        user.setRole(normalizedRoleCode);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("beforeRole", beforeRole);
        detail.put("afterRole", normalizedRoleCode);
        auditService.record(operatorId, operatorRole, "USER_ROLE_UPDATED", "SYS_USER", userId, detail, ip, userAgent);
    }

    @Transactional
    public void resetPassword(String userId,
                              ResetPasswordRequest request,
                              String operatorId,
                              String operatorRole,
                              String ip,
                              String userAgent) {
        guardMutationDedup(operatorId, "user-password-reset", userId);

        String normalizedPassword = request.getNewPassword() == null ? "" : request.getNewPassword().trim();
        if (!STRONG_PASSWORD_PATTERN.matcher(normalizedPassword).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "Password must contain upper/lowercase letters, digits and special characters, length 8-64");
        }

        SysUserEntity user = requireUser(userId);
        user.setPasswordHash(passwordEncoder.encode(normalizedPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("username", user.getUsername());
        detail.put("passwordReset", true);
        if (StringUtils.hasText(request.getReason())) {
            detail.put("reason", request.getReason().trim());
        }
        auditService.record(operatorId, operatorRole, "USER_PASSWORD_RESET", "SYS_USER", userId, detail, ip, userAgent);
    }

    public List<Map<String, Object>> listRoles() {
        List<Map<String, Object>> cached = getCache(ROLES_CACHE_KEY, new TypeReference<List<Map<String, Object>>>() {
        });
        if (cached != null) {
            return cached;
        }

        List<SysRoleEntity> roles = roleMapper.selectList(
                Wrappers.lambdaQuery(SysRoleEntity.class)
                        .orderByDesc(SysRoleEntity::getIsSystem)
                        .orderByAsc(SysRoleEntity::getRoleCode)
        );
        if (roles.isEmpty()) {
            return List.of();
        }

        List<SysPermissionEntity> permissions = permissionMapper.selectList(
                Wrappers.lambdaQuery(SysPermissionEntity.class)
                        .orderByAsc(SysPermissionEntity::getModuleKey)
                        .orderByAsc(SysPermissionEntity::getPermissionCode)
        );
        Map<String, SysPermissionEntity> permissionMap = permissions.stream()
                .collect(Collectors.toMap(
                        SysPermissionEntity::getPermissionCode,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<String> roleCodes = roles.stream()
                .map(SysRoleEntity::getRoleCode)
                .filter(StringUtils::hasText)
                .toList();

        Map<String, List<String>> permissionCodesByRole = new HashMap<>();
        if (!roleCodes.isEmpty()) {
            List<SysRolePermissionEntity> relations = rolePermissionMapper.selectList(
                    Wrappers.lambdaQuery(SysRolePermissionEntity.class)
                            .in(SysRolePermissionEntity::getRoleCode, roleCodes)
            );
            for (SysRolePermissionEntity relation : relations) {
                permissionCodesByRole
                        .computeIfAbsent(relation.getRoleCode(), ignored -> new ArrayList<>())
                        .add(relation.getPermissionCode());
            }
        }

        List<Map<String, Object>> payload = new ArrayList<>();
        for (SysRoleEntity role : roles) {
            List<Map<String, Object>> rolePermissions = new ArrayList<>();
            List<String> codes = permissionCodesByRole.getOrDefault(role.getRoleCode(), List.of());
            for (String code : codes) {
                SysPermissionEntity permission = permissionMap.get(code);
                if (permission != null) {
                    rolePermissions.add(toPermissionPayload(permission));
                }
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", role.getId() == null ? null : String.valueOf(role.getId()));
            item.put("roleCode", role.getRoleCode());
            item.put("roleName", role.getRoleName());
            item.put("description", role.getDescription());
            item.put("isSystem", role.getIsSystem());
            item.put("status", role.getStatus());
            item.put("permissions", rolePermissions);
            payload.add(item);
        }

        putCache(ROLES_CACHE_KEY, payload, ROLE_PERMISSION_CACHE_TTL);
        return payload;
    }

    public List<Map<String, Object>> listPermissions() {
        List<Map<String, Object>> cached = getCache(PERMISSIONS_CACHE_KEY, new TypeReference<List<Map<String, Object>>>() {
        });
        if (cached != null) {
            return cached;
        }

        List<SysPermissionEntity> permissions = permissionMapper.selectList(
                Wrappers.lambdaQuery(SysPermissionEntity.class)
                        .orderByAsc(SysPermissionEntity::getModuleKey)
                        .orderByAsc(SysPermissionEntity::getPermissionCode)
        );
        List<Map<String, Object>> payload = permissions.stream().map(this::toPermissionPayload).toList();
        putCache(PERMISSIONS_CACHE_KEY, payload, ROLE_PERMISSION_CACHE_TTL);
        return payload;
    }

    @Transactional
    public void updateRolePermissions(String roleCode,
                                      UpdateRolePermissionsRequest request,
                                      String operatorId,
                                      String operatorRole,
                                      String ip,
                                      String userAgent) {
        String normalizedRoleCode = normalizeCode(roleCode);
        guardMutationDedup(operatorId, "role-permissions", normalizedRoleCode);
        requireRole(normalizedRoleCode);

        Set<String> requestedCodes = request.getPermissionCodes().stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedCodes.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "permissionCodes cannot be empty");
        }

        List<SysPermissionEntity> existingPermissions = permissionMapper.selectList(
                Wrappers.lambdaQuery(SysPermissionEntity.class)
                        .in(SysPermissionEntity::getPermissionCode, requestedCodes)
                        .eq(SysPermissionEntity::getStatus, 1)
        );
        Set<String> existingCodes = existingPermissions.stream()
                .map(SysPermissionEntity::getPermissionCode)
                .collect(Collectors.toSet());

        if (existingCodes.size() != requestedCodes.size()) {
            List<String> missing = requestedCodes.stream()
                    .filter(code -> !existingCodes.contains(code))
                    .sorted()
                    .toList();
            throw new BizException(ErrorCode.BAD_REQUEST, "Unknown or disabled permissions: " + String.join(", ", missing));
        }

        rolePermissionMapper.delete(
                Wrappers.lambdaQuery(SysRolePermissionEntity.class)
                        .eq(SysRolePermissionEntity::getRoleCode, normalizedRoleCode)
        );
        LocalDateTime now = LocalDateTime.now();
        for (String permissionCode : requestedCodes) {
            SysRolePermissionEntity relation = new SysRolePermissionEntity();
            relation.setRoleCode(normalizedRoleCode);
            relation.setPermissionCode(permissionCode);
            relation.setCreatedAt(now);
            rolePermissionMapper.insert(relation);
        }

        evictRolePermissionCache();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("permissionCount", requestedCodes.size());
        detail.put("permissionCodes", requestedCodes);
        auditService.record(
                operatorId,
                operatorRole,
                "ROLE_PERMISSIONS_UPDATED",
                "SYS_ROLE",
                normalizedRoleCode,
                detail,
                ip,
                userAgent
        );
    }

    public Map<String, Object> listConfigs(String groupKey, String keyword) {
        String normalizedGroup = normalizeGroupKey(groupKey);
        String trimmedKeyword = trimToNull(keyword);

        var query = Wrappers.lambdaQuery(SysConfigEntity.class);
        if (StringUtils.hasText(normalizedGroup)) {
            query.eq(SysConfigEntity::getGroupKey, normalizedGroup);
        }
        if (StringUtils.hasText(trimmedKeyword)) {
            query.and(wrapper -> wrapper
                    .like(SysConfigEntity::getConfigKey, trimmedKeyword)
                    .or()
                    .like(SysConfigEntity::getDescription, trimmedKeyword));
        }
        query.orderByAsc(SysConfigEntity::getGroupKey)
                .orderByAsc(SysConfigEntity::getConfigKey);

        List<Map<String, Object>> records = configMapper.selectList(query).stream().map(this::toConfigPayload).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", records.size());
        payload.put("records", records);
        return payload;
    }

    @Transactional
    public void upsertConfig(String configKey,
                             UpsertConfigRequest request,
                             String operatorId,
                             String operatorRole,
                             String ip,
                             String userAgent) {
        String normalizedConfigKey = normalizeConfigKey(configKey);
        guardMutationDedup(operatorId, "config-upsert", normalizedConfigKey);

        SysConfigEntity existing = configMapper.selectById(normalizedConfigKey);
        LocalDateTime now = LocalDateTime.now();
        Long operatorLongId = parseLong("operatorId", operatorId);
        String groupKey = normalizeGroupKey(request.getGroupKey());
        if (!StringUtils.hasText(groupKey)) {
            groupKey = existing == null ? "SYSTEM" : existing.getGroupKey();
        }

        if (existing == null) {
            SysConfigEntity entity = new SysConfigEntity();
            entity.setConfigKey(normalizedConfigKey);
            entity.setConfigValue(request.getConfigValue());
            entity.setGroupKey(groupKey);
            entity.setDescription(trimToNull(request.getDescription()));
            entity.setUpdatedBy(operatorLongId);
            entity.setUpdatedAt(now);
            configMapper.insert(entity);
        } else {
            existing.setConfigValue(request.getConfigValue());
            existing.setGroupKey(groupKey);
            existing.setDescription(trimToNull(request.getDescription()));
            existing.setUpdatedBy(operatorLongId);
            existing.setUpdatedAt(now);
            configMapper.updateById(existing);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("groupKey", groupKey);
        detail.put("description", trimToNull(request.getDescription()));
        auditService.record(operatorId, operatorRole, "SYSTEM_CONFIG_UPSERTED", "SYS_CONFIG", normalizedConfigKey, detail, ip, userAgent);
    }

    public Map<String, Object> listAuditLogs(String operatorId,
                                             String action,
                                             String targetType,
                                             LocalDateTime startTime,
                                             LocalDateTime endTime,
                                             Long page,
                                             Long size) {
        long safePage = normalizePage(page);
        long safeSize = normalizeSize(size);
        long offset = (safePage - 1) * safeSize;

        Long total = auditLogMapper.selectCount(buildAuditQuery(operatorId, action, targetType, startTime, endTime, false, 0, 0));
        List<Map<String, Object>> records = List.of();
        if (total != null && total > 0) {
            List<SysAuditLogEntity> entities = auditLogMapper.selectList(
                    buildAuditQuery(operatorId, action, targetType, startTime, endTime, true, offset, safeSize)
            );
            records = entities.stream().map(this::toAuditPayload).toList();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", safePage);
        payload.put("size", safeSize);
        payload.put("total", total == null ? 0L : total);
        payload.put("records", records);
        return payload;
    }

    private SysUserEntity requireUser(String userId) {
        SysUserEntity user = userMapper.selectById(parseLong("userId", userId));
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "User not found");
        }
        return user;
    }

    private SysRoleEntity requireRole(String roleCode) {
        SysRoleEntity role = roleMapper.selectOne(
                Wrappers.lambdaQuery(SysRoleEntity.class)
                        .eq(SysRoleEntity::getRoleCode, roleCode)
                        .last("limit 1")
        );
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Role not found: " + roleCode);
        }
        if (role.getStatus() == null || role.getStatus() != 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Role is disabled: " + roleCode);
        }
        return role;
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserEntity> buildUserQuery(
            String keyword,
            String role,
            Integer status,
            boolean withSortAndLimit,
            long offset,
            long size) {
        String trimmedKeyword = trimToNull(keyword);
        String normalizedRole = StringUtils.hasText(role) ? normalizeCode(role) : null;

        var query = Wrappers.lambdaQuery(SysUserEntity.class);
        if (StringUtils.hasText(trimmedKeyword)) {
            query.and(wrapper -> wrapper
                    .like(SysUserEntity::getUsername, trimmedKeyword)
                    .or()
                    .like(SysUserEntity::getRealName, trimmedKeyword));
        }
        if (StringUtils.hasText(normalizedRole)) {
            query.eq(SysUserEntity::getRole, normalizedRole);
        }
        if (status != null) {
            query.eq(SysUserEntity::getStatus, status);
        }
        if (withSortAndLimit) {
            query.orderByDesc(SysUserEntity::getUpdatedAt)
                    .orderByAsc(SysUserEntity::getId)
                    .last("limit " + offset + "," + size);
        }
        return query;
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysAuditLogEntity> buildAuditQuery(
            String operatorId,
            String action,
            String targetType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            boolean withSortAndLimit,
            long offset,
            long size) {
        var query = Wrappers.lambdaQuery(SysAuditLogEntity.class);

        if (StringUtils.hasText(operatorId)) {
            query.eq(SysAuditLogEntity::getOperatorId, parseLong("operatorId", operatorId));
        }
        if (StringUtils.hasText(action)) {
            query.eq(SysAuditLogEntity::getAction, normalizeCode(action));
        }
        if (StringUtils.hasText(targetType)) {
            query.eq(SysAuditLogEntity::getTargetType, normalizeCode(targetType));
        }
        if (startTime != null) {
            query.ge(SysAuditLogEntity::getCreatedAt, startTime);
        }
        if (endTime != null) {
            query.le(SysAuditLogEntity::getCreatedAt, endTime);
        }
        if (withSortAndLimit) {
            query.orderByDesc(SysAuditLogEntity::getCreatedAt)
                    .orderByDesc(SysAuditLogEntity::getId)
                    .last("limit " + offset + "," + size);
        }
        return query;
    }

    private Map<String, Object> toUserPayload(SysUserEntity user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", user.getId() == null ? null : String.valueOf(user.getId()));
        payload.put("username", user.getUsername());
        payload.put("realName", user.getRealName());
        payload.put("role", user.getRole());
        payload.put("status", user.getStatus());
        payload.put("statusLabel", user.getStatus() != null && user.getStatus() == 1 ? "ENABLED" : "DISABLED");
        payload.put("createdAt", user.getCreatedAt());
        payload.put("updatedAt", user.getUpdatedAt());
        return payload;
    }

    private Map<String, Object> toPermissionPayload(SysPermissionEntity permission) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", permission.getId() == null ? null : String.valueOf(permission.getId()));
        payload.put("permissionCode", permission.getPermissionCode());
        payload.put("permissionName", permission.getPermissionName());
        payload.put("moduleKey", permission.getModuleKey());
        payload.put("description", permission.getDescription());
        payload.put("status", permission.getStatus());
        return payload;
    }

    private Map<String, Object> toConfigPayload(SysConfigEntity config) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("configKey", config.getConfigKey());
        payload.put("configValue", config.getConfigValue());
        payload.put("groupKey", config.getGroupKey());
        payload.put("description", config.getDescription());
        payload.put("updatedBy", config.getUpdatedBy() == null ? null : String.valueOf(config.getUpdatedBy()));
        payload.put("updatedAt", config.getUpdatedAt());
        return payload;
    }

    private Map<String, Object> toAuditPayload(SysAuditLogEntity entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", entity.getId() == null ? null : String.valueOf(entity.getId()));
        payload.put("operatorId", entity.getOperatorId() == null ? null : String.valueOf(entity.getOperatorId()));
        payload.put("operatorRole", entity.getOperatorRole());
        payload.put("action", entity.getAction());
        payload.put("targetType", entity.getTargetType());
        payload.put("targetId", entity.getTargetId());
        payload.put("detail", parseJsonOrRaw(entity.getDetailJson()));
        payload.put("ip", entity.getIp());
        payload.put("userAgent", entity.getUserAgent());
        payload.put("createdAt", entity.getCreatedAt());
        return payload;
    }

    private Object parseJsonOrRaw(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, Object.class);
        } catch (Exception ex) {
            return raw;
        }
    }

    private void guardMutationDedup(String operatorId, String action, String target) {
        String dedupKey = MUTATION_DEDUP_PREFIX + operatorId + ":" + action + ":" + target;
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", MUTATION_DEDUP_TTL);
            if (Boolean.FALSE.equals(ok)) {
                throw new BizException(ErrorCode.CONFLICT, "Duplicate operation request");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Mutation dedup unavailable, key={}", dedupKey, ex);
        }
    }

    private void evictOverviewCache() {
        try {
            redisTemplate.delete(OVERVIEW_CACHE_KEY);
        } catch (Exception ex) {
            log.warn("Failed to evict overview cache", ex);
        }
    }

    private void evictRolePermissionCache() {
        try {
            redisTemplate.delete(List.of(ROLES_CACHE_KEY, PERMISSIONS_CACHE_KEY));
        } catch (Exception ex) {
            log.warn("Failed to evict role/permission cache", ex);
        }
    }

    private long safeCount(Long value) {
        return value == null ? 0L : value;
    }

    private long normalizePage(Long page) {
        if (page == null || page < 1) {
            return 1L;
        }
        return page;
    }

    private long normalizeSize(Long size) {
        if (size == null || size < 1) {
            return 20L;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeCode(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return "";
        }
        return rawValue.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeConfigKey(String configKey) {
        String normalized = normalizeCode(configKey);
        if (!normalized.matches("[A-Z0-9_.-]{3,128}")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid configKey format");
        }
        return normalized;
    }

    private String normalizeGroupKey(String groupKey) {
        String normalized = normalizeCode(groupKey);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.length() > 64) {
            throw new BizException(ErrorCode.BAD_REQUEST, "groupKey is too long");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private long parseLong(String fieldName, String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            throw new BizException(ErrorCode.BAD_REQUEST, fieldName + " is required");
        }
        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid " + fieldName + ": " + rawValue);
        }
    }

    private <T> T getCache(String key, TypeReference<T> typeReference) {
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            return objectMapper.readValue(raw, typeReference);
        } catch (Exception ex) {
            log.warn("Failed to read cache, key={}", key, ex);
            return null;
        }
    }

    private void putCache(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize cache payload, key={}", key, ex);
        } catch (Exception ex) {
            log.warn("Failed to write cache, key={}", key, ex);
        }
    }
}
