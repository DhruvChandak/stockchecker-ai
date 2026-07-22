package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "import_session_files")
public class ImportSessionFile extends TenantOwnedEntity {
    public UUID importSessionId;
    public String originalFileName;
    public String contentType;
    public String storageKey;
    public String fileHash;

    @Enumerated(EnumType.STRING)
    public DomainEnums.DetectedFileType detectedFileType = DomainEnums.DetectedFileType.UNKNOWN;

    @Enumerated(EnumType.STRING)
    public DomainEnums.DetectedFileType selectedFileType;

    public BigDecimal confidence = BigDecimal.ZERO;
    public String detectionReason;

    @Enumerated(EnumType.STRING)
    public DomainEnums.ImportSessionFileStatus status = DomainEnums.ImportSessionFileStatus.UPLOADED;

    public int rowCount;
    public int errorCount;
    public int warningCount;
    public LocalDate dateRangeStart;
    public LocalDate dateRangeEnd;
    public String companyName;

    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> metadataJson = new LinkedHashMap<>();
}
