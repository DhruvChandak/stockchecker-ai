package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sales_order_items")
public class SalesOrderItem extends TenantOwnedEntity {
    public UUID salesOrderId;
    public UUID productId;
    public BigDecimal quantity = BigDecimal.ZERO;
    public UUID unitId;
    public BigDecimal rate = BigDecimal.ZERO;
    public BigDecimal lineTotal = BigDecimal.ZERO;
}
