package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "customers")
public class Customer extends TenantOwnedEntity {
    public String name;
    public String phone;
    public String email;
    public String gstin;
    public BigDecimal creditLimit = BigDecimal.ZERO;
    public BigDecimal openingBalance = BigDecimal.ZERO;
}
