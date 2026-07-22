package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "smart_import_resolutions")
public class SmartImportResolution extends TenantOwnedEntity {
    public UUID importSessionId;
    public String resolutionKey;
    public String action;
    public UUID targetId;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> detailsJson = new LinkedHashMap<>();
}
