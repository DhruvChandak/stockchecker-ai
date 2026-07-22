package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "smart_staged_units")
public class SmartStagedUnit extends TenantOwnedEntity {
    public UUID importSessionId;
    public UUID sessionFileId;
    public int sourceRowNumber;
    public String sourceUnitCode;
    public String normalizedCode;
    public UUID matchedUnitId;
    @Enumerated(EnumType.STRING)
    public DomainEnums.SmartMatchStatus matchStatus = DomainEnums.SmartMatchStatus.UNKNOWN;
    @Enumerated(EnumType.STRING)
    public DomainEnums.SmartReviewStatus reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
}
