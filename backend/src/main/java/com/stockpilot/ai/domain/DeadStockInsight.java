package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "dead_stock_insights")
public class DeadStockInsight extends TenantOwnedEntity {
    public UUID productId;
    public UUID warehouseId;
    public BigDecimal stockQuantity = BigDecimal.ZERO;
    public BigDecimal stockValue = BigDecimal.ZERO;
    public LocalDate lastSoldDate;
    public String suggestedAction;
    public String explanation;
    public Instant generatedAt = Instant.now();
}
