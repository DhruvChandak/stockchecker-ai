package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "import_effects")
public class ImportEffect extends TenantOwnedEntity {
    public UUID importBatchId;
    @Enumerated(EnumType.STRING)
    public DomainEnums.ImportEffectEntityType entityType;
    public UUID entityId;
    @Enumerated(EnumType.STRING)
    public DomainEnums.ImportEffectAction action = DomainEnums.ImportEffectAction.CREATED;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> oldValueJson = new HashMap<>();
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> newValueJson = new HashMap<>();
    public boolean reversible = true;
    public Instant reversedAt;
}
