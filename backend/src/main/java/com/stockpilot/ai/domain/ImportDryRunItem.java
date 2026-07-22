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
@Table(name = "import_dry_run_items")
public class ImportDryRunItem extends TenantOwnedEntity {
    public UUID importSessionId;
    public UUID dryRunId;

    @Enumerated(EnumType.STRING)
    public DomainEnums.ImportDryRunItemType itemType;

    @Enumerated(EnumType.STRING)
    public DomainEnums.ImportDryRunAction action;

    public UUID sourceFileId;
    public Integer sourceRowNumber;
    public UUID targetEntityId;

    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> previewJson = new LinkedHashMap<>();

    public String warningCode;
    public String errorCode;
}
