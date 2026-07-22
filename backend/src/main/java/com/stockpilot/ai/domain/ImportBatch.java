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

@Entity
@Table(name = "import_batches")
public class ImportBatch extends TenantOwnedEntity {
    @Enumerated(EnumType.STRING)
    public DomainEnums.SourceType sourceType;
    @Enumerated(EnumType.STRING)
    public DomainEnums.ImportStatus status = DomainEnums.ImportStatus.UPLOADED;
    public String originalFileName;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> mappingJson = new HashMap<>();
    public int rowCount = 0;
    public int validCount = 0;
    public int errorCount = 0;
    public Instant committedAt;
    @Enumerated(EnumType.STRING)
    public DomainEnums.ImportPurpose importPurpose = DomainEnums.ImportPurpose.MASTER_IMPORT;
    public Instant rolledBackAt;
}
