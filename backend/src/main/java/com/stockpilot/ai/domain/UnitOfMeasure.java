package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "units_of_measure")
public class UnitOfMeasure extends TenantOwnedEntity {
    public String code;
    public String name;
    public boolean baseUnit = false;
}
