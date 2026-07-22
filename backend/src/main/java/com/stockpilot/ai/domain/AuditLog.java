package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseAudit {
    public UUID tenantId;
    public UUID actorUserId;
    public String action;
    public String entityType;
    public UUID entityId;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> detailsJson = new HashMap<>();
    public String ipAddress;
}
