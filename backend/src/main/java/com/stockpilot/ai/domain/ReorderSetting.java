package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "reorder_settings")
public class ReorderSetting extends TenantOwnedEntity {
    public UUID productId;
    public UUID warehouseId;
    public BigDecimal reorderPoint = BigDecimal.ZERO;
    public BigDecimal safetyStock = BigDecimal.ZERO;
    public int leadTimeDays = 7;
    public BigDecimal minimumOrderQuantity = BigDecimal.ZERO;
}
