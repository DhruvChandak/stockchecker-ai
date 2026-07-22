package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "customer_price_lists")
public class CustomerPriceList extends TenantOwnedEntity {
    public UUID customerId;
    public UUID productId;
    public BigDecimal price = BigDecimal.ZERO;
}
