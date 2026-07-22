package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder extends TenantOwnedEntity {
    public UUID supplierId;
    public String orderNumber;
    public String status = "OPEN";
    public BigDecimal totalAmount = BigDecimal.ZERO;
}
