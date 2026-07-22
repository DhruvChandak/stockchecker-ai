package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "product_barcodes")
public class ProductBarcode extends TenantOwnedEntity {
    public UUID productId;
    public String barcode;
}
