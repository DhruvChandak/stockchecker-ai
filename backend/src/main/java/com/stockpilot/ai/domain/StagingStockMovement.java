package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "staging_stock_movements")
public class StagingStockMovement extends TenantOwnedEntity {
    public UUID importBatchId;
    public int rowNumber;
    public String productName;
    public String warehouseName;
    public String movementType;
    public BigDecimal quantity;
    public BigDecimal rate;
    public LocalDate movementDate;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> rawMetadata = new HashMap<>();
    public boolean committed = false;
}
