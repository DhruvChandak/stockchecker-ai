package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "smart_import_effects")
public class SmartImportEffect extends TenantOwnedEntity {
    public UUID importSessionId;
    public UUID commitId;
    public UUID sourceFileId;
    public Integer sourceRowNumber;

    @Enumerated(EnumType.STRING)
    public DomainEnums.SmartImportEffectEntityType entityType;

    public UUID entityId;

    @Enumerated(EnumType.STRING)
    public DomainEnums.SmartImportEffectAction action;

    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> oldValueJson = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> newValueJson = new LinkedHashMap<>();

    public boolean reversible;
    public Instant reversedAt;
}
