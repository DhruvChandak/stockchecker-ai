package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "import_plans")
public class ImportPlan extends TenantOwnedEntity {
    public UUID importSessionId;

    @Enumerated(EnumType.STRING)
    public DomainEnums.ImportStrategy strategy = DomainEnums.ImportStrategy.UNKNOWN;

    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> planJson = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    public DomainEnums.ImportPlanStatus status = DomainEnums.ImportPlanStatus.DRAFT;
}
