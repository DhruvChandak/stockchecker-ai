package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "product_batches")
public class ProductBatch extends TenantOwnedEntity {
    public UUID productId;
    public String batchNo;
    public LocalDate expiryDate;
    public LocalDate manufacturingDate;
}
