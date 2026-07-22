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
@Table(name = "outstanding_snapshots")
public class OutstandingSnapshot extends TenantOwnedEntity {
    @Enumerated(EnumType.STRING)
    public DomainEnums.OutstandingPartyType partyType;
    public UUID customerId;
    public UUID supplierId;
    public LocalDate snapshotDate;
    public BigDecimal outstandingAmount = BigDecimal.ZERO;
    public BigDecimal overdueAmount;
    public UUID sourceImportSessionId;
    public UUID sourceFileId;
    public Integer sourceRowNumber;
    public String sourceFingerprint;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> rawMetadataJson = new LinkedHashMap<>();
}
