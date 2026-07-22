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
@Table(name = "smart_import_commits")
public class SmartImportCommit extends TenantOwnedEntity {
    public UUID importSessionId;
    public UUID dryRunId;

    @Enumerated(EnumType.STRING)
    public DomainEnums.SmartImportCommitStatus status = DomainEnums.SmartImportCommitStatus.COMMITTING;

    @Enumerated(EnumType.STRING)
    public DomainEnums.ImportStrategy strategy = DomainEnums.ImportStrategy.UNKNOWN;

    public Instant startedAt;
    public Instant finishedAt;
    public UUID committedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> summaryJson = new LinkedHashMap<>();
}
