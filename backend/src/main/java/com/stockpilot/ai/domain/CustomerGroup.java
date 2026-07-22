package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "customer_groups")
public class CustomerGroup extends TenantOwnedEntity {
    public String name;
    public BigDecimal discountPercentage = BigDecimal.ZERO;
}
