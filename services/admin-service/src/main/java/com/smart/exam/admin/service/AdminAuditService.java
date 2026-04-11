package com.smart.exam.admin.service;

import com.smart.exam.common.web.audit.AuditLogCommand;
import com.smart.exam.common.web.audit.AuditLogService;
import com.smart.exam.common.web.audit.AuditModules;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditService {

    private final AuditLogService auditLogService;

    public AdminAuditService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public void record(String operatorId,
                       String operatorRole,
                       String action,
                       String targetType,
                       String targetId,
                       Object detail,
                       String ip,
                       String userAgent) {
        auditLogService.record(
                AuditLogCommand.builder()
                        .moduleKey(AuditModules.ADMIN)
                        .operatorId(operatorId)
                        .operatorRole(operatorRole)
                        .action(action)
                        .targetType(targetType)
                        .targetId(targetId)
                        .detail(detail)
                        .ip(ip)
                        .userAgent(userAgent)
                        .build()
        );
    }
}
