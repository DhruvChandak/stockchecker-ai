package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_invoice_items")
public class PurchaseInvoiceItem extends TenantOwnedEntity {
    public UUID purchaseInvoiceId;
    public UUID productId;
    public BigDecimal quantity = BigDecimal.ZERO;
    public UUID unitId;
    public BigDecimal rate = BigDecimal.ZERO;
    public BigDecimal taxPercentage = BigDecimal.ZERO;
    public BigDecimal taxAmount = BigDecimal.ZERO;
    public BigDecimal lineTotal = BigDecimal.ZERO;
}
