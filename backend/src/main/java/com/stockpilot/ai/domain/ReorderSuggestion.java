package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reorder_suggestions")
public class ReorderSuggestion extends TenantOwnedEntity {
    public UUID productId;
    public UUID warehouseId;
    public BigDecimal reorderPoint = BigDecimal.ZERO;
    public BigDecimal suggestedQuantity = BigDecimal.ZERO;
    public String reason;
    public String status = "OPEN";
    public Instant generatedAt = Instant.now();
}
