package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "staging_products")
public class StagingProduct extends TenantOwnedEntity {
    public UUID importBatchId;
    public int rowNumber;
    public String productName;
    public String sku;
    public String category;
    public String brand;
    public String unitCode;
    public String barcode;
    public String hsnCode;
    public BigDecimal gstPercentage;
    public BigDecimal openingStock;
    public BigDecimal purchasePrice;
    public BigDecimal salesPrice;
    public String warehouseName;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> rawMetadata = new HashMap<>();
    public boolean committed = false;
}
