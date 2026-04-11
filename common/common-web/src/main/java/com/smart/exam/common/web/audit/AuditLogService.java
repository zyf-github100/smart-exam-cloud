package com.smart.exam.common.web.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.exam.common.core.id.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final String INSERT_SQL = """
            INSERT INTO admin_db.sys_audit_log (
                id,
                service_name,
                module_key,
                operator_id,
                operator_role,
                action,
                target_type,
                target_id,
                detail_json,
                ip,
                user_agent,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final SnowflakeIdGenerator idGenerator;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public AuditLogService(SnowflakeIdGenerator idGenerator,
                           JdbcTemplate jdbcTemplate,
                           ObjectMapper objectMapper,
                           @Value("${spring.application.name:unknown-service}") String serviceName) {
        this.idGenerator = idGenerator;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

    public void record(AuditLogCommand command) {
        try {
            jdbcTemplate.update(
                    INSERT_SQL,
                    idGenerator.nextId(),
                    trimOrDefault(serviceName, "unknown-service"),
                    trimOrDefault(command.moduleKey(), "GENERAL"),
                    parseLongOrDefault(command.operatorId(), -1L),
                    trimOrDefault(command.operatorRole(), "UNKNOWN"),
                    trimOrDefault(command.action(), "UNKNOWN_ACTION"),
                    trimOrDefault(command.targetType(), "UNKNOWN_TARGET"),
                    trimOrDefault(command.targetId(), "-"),
                    writeJson(command.detail()),
                    trimOrDefault(command.ip(), "-"),
                    trimOrDefault(command.userAgent(), "-"),
                    LocalDateTime.now()
            );
        } catch (Exception ex) {
            log.warn(
                    "Failed to write audit log, serviceName={}, moduleKey={}, action={}, targetType={}, targetId={}",
                    serviceName,
                    command.moduleKey(),
                    command.action(),
                    command.targetType(),
                    command.targetId(),
                    ex
            );
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
