package com.smart.exam.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.exam.admin.entity.SysAuditLogEntity;
import com.smart.exam.admin.mapper.SysAuditLogMapper;
import com.smart.exam.common.core.id.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);

    private final SnowflakeIdGenerator idGenerator;
    private final SysAuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AdminAuditService(SnowflakeIdGenerator idGenerator,
                             SysAuditLogMapper auditLogMapper,
                             ObjectMapper objectMapper) {
        this.idGenerator = idGenerator;
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String operatorId,
                       String operatorRole,
                       String action,
                       String targetType,
                       String targetId,
                       Object detail,
                       String ip,
                       String userAgent) {
        try {
            SysAuditLogEntity entity = new SysAuditLogEntity();
            entity.setId(idGenerator.nextId());
            entity.setOperatorId(parseLongOrDefault(operatorId, -1L));
            entity.setOperatorRole(StringUtils.hasText(operatorRole) ? operatorRole.trim() : "UNKNOWN");
            entity.setAction(StringUtils.hasText(action) ? action.trim() : "UNKNOWN_ACTION");
            entity.setTargetType(StringUtils.hasText(targetType) ? targetType.trim() : "UNKNOWN_TARGET");
            entity.setTargetId(StringUtils.hasText(targetId) ? targetId.trim() : "-");
            entity.setDetailJson(writeJson(detail));
            entity.setIp(trimOrDefault(ip, "-"));
            entity.setUserAgent(trimOrDefault(userAgent, "-"));
            entity.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(entity);
        } catch (Exception ex) {
            log.warn("Failed to write admin audit log, action={}, targetType={}, targetId={}",
                    action, targetType, targetId, ex);
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"serialization\":\"failed\"}";
        }
    }

    private Long parseLongOrDefault(String value, long defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String trimOrDefault(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return value.trim();
    }
}
