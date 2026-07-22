package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "product_categories")
public class ProductCategory extends TenantOwnedEntity {
    public String name;
    public UUID parentId;
}
