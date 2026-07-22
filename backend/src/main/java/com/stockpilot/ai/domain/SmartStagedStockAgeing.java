package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "smart_staged_stock_ageing")
public class SmartStagedStockAgeing extends TenantOwnedEntity {
    public UUID importSessionId;
    public UUID sessionFileId;
    public int sourceRowNumber;
    public String productName;
    public String normalizedProductName;
    public String unitCode;
    public BigDecimal quantity;
    public String ageingBucket;
    public Integer daysOld;
    public BigDecimal stockValue;
    public UUID matchedProductId;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> rawMetadataJson = new LinkedHashMap<>();
    @Enumerated(EnumType.STRING)
    public DomainEnums.SmartMatchStatus matchStatus = DomainEnums.SmartMatchStatus.UNKNOWN;
    @Enumerated(EnumType.STRING)
    public DomainEnums.SmartReviewStatus reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
}
