package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_items")
public class PurchaseOrderItem extends TenantOwnedEntity {
    public UUID purchaseOrderId;
    public UUID productId;
    public UUID warehouseId;
    public BigDecimal quantity = BigDecimal.ZERO;
    public UUID unitId;
    public BigDecimal rate = BigDecimal.ZERO;
    public BigDecimal lineTotal = BigDecimal.ZERO;
}
