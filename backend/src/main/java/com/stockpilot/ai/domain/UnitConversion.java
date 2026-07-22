package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "unit_conversions")
public class UnitConversion extends TenantOwnedEntity {
    public UUID productId;
    public UUID fromUnitId;
    public UUID toUnitId;
    public BigDecimal multiplier = BigDecimal.ONE;
}
