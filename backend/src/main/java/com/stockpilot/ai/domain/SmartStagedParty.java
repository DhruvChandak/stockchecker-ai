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
@Table(name = "smart_staged_parties")
public class SmartStagedParty extends TenantOwnedEntity {
    public UUID importSessionId;
    public UUID sessionFileId;
    public int sourceRowNumber;
    @Enumerated(EnumType.STRING)
    public DomainEnums.DetectedFileType sourceType = DomainEnums.DetectedFileType.ACCOUNTING_MASTER;
    @Enumerated(EnumType.STRING)
    public DomainEnums.SmartPartyType partyType = DomainEnums.SmartPartyType.UNKNOWN;
    public String rawName;
    public String normalizedName;
    public String gstin;
    public String phone;
    public String email;
    public BigDecimal openingBalance;
    public LocalDate snapshotDate;
    public UUID matchedCustomerId;
    public UUID matchedSupplierId;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> rawMetadataJson = new LinkedHashMap<>();
    @Enumerated(EnumType.STRING)
    public DomainEnums.SmartMatchStatus matchStatus = DomainEnums.SmartMatchStatus.UNKNOWN;
    @Enumerated(EnumType.STRING)
    public DomainEnums.SmartReviewStatus reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
}
