package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "import_sessions")
public class ImportSession extends TenantOwnedEntity {
    public String name;

    @Enumerated(EnumType.STRING)
    public DomainEnums.ImportSessionStatus status = DomainEnums.ImportSessionStatus.CREATED;

    @Enumerated(EnumType.STRING)
    public DomainEnums.ImportStrategy recommendedStrategy = DomainEnums.ImportStrategy.UNKNOWN;

    public Instant committedAt;
}
