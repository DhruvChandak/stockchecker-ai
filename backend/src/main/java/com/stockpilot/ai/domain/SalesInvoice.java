package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "sales_invoices")
public class SalesInvoice extends TenantOwnedEntity {
    public UUID customerId;
    public UUID warehouseId;
    public String invoiceNumber;
    public LocalDate invoiceDate;
    public BigDecimal subtotal = BigDecimal.ZERO;
    public BigDecimal taxAmount = BigDecimal.ZERO;
    public BigDecimal discountAmount = BigDecimal.ZERO;
    public BigDecimal freightAmount = BigDecimal.ZERO;
    public BigDecimal roundOffAmount = BigDecimal.ZERO;
    public BigDecimal otherChargesAmount = BigDecimal.ZERO;
    public BigDecimal totalAmount = BigDecimal.ZERO;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> financialMetadata = new LinkedHashMap<>();
}
