package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "suppliers")
public class Supplier extends TenantOwnedEntity {
    public String name;
    public String phone;
    public String email;
    public String gstin;
    public int creditDays = 0;
    public BigDecimal openingBalance = BigDecimal.ZERO;
}
