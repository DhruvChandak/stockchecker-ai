package com.stockpilot.ai.service;

import com.stockpilot.ai.config.TenantContext;
import com.stockpilot.ai.domain.AuditLog;
import com.stockpilot.ai.repo.Repositories;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
    private final Repositories.AuditLogRepository auditLogs;

    public AuditService(Repositories.AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    public void log(UUID tenantId, UUID actorUserId, String action, String entityType, UUID entityId, Map<String, Object> details) {
        var audit = new AuditLog();
        audit.tenantId = tenantId;
        audit.actorUserId = actorUserId;
        audit.action = action;
        audit.entityType = entityType;
        audit.entityId = entityId;
        audit.detailsJson = details == null ? Map.of() : details;
        auditLogs.save(audit);
    }

    public void logCurrent(String action, String entityType, UUID entityId, Map<String, Object> details) {
        log(TenantContext.tenantId(), TenantContext.userId(), action, entityType, entityId, details);
    }
}
