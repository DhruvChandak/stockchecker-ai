package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "product_tax_infos")
public class ProductTaxInfo extends TenantOwnedEntity {
    public UUID productId;
    public String hsnCode;
    public BigDecimal gstPercentage = BigDecimal.ZERO;
    public BigDecimal cessPercentage = BigDecimal.ZERO;
}
