package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "warehouses")
public class Warehouse extends TenantOwnedEntity {
    public UUID branchId;
    public String name;
    public String code;
    public String address;
    public boolean active = true;
}
