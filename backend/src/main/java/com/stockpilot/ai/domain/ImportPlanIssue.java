package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "import_plan_issues")
public class ImportPlanIssue extends TenantOwnedEntity {
    public UUID importSessionId;

    @Enumerated(EnumType.STRING)
    public DomainEnums.ImportPlanIssueSeverity severity;

    public String code;
    public String message;
    public UUID affectedFileId;

    @JdbcTypeCode(SqlTypes.JSON)
    public List<Integer> affectedRows = new ArrayList<>();

    public String suggestedAction;
    public boolean resolved;

    @JdbcTypeCode(SqlTypes.JSON)
    public List<Map<String, Object>> availableChoices = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> contextJson = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> resolutionJson = new LinkedHashMap<>();
}
