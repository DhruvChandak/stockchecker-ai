package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "branches")
public class Branch extends TenantOwnedEntity {
    public String name;
    public String city;
    public boolean active = true;
}
