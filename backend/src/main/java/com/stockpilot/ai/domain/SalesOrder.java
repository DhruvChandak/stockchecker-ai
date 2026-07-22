package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sales_orders")
public class SalesOrder extends TenantOwnedEntity {
    public UUID customerId;
    public String orderNumber;
    public String status = "OPEN";
    public BigDecimal totalAmount = BigDecimal.ZERO;
}
