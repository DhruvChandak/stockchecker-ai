package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "staging_invoices")
public class StagingInvoice extends TenantOwnedEntity {
    public UUID importBatchId;
    public int rowNumber;
    public String invoiceType;
    public String invoiceNumber;
    public String partyName;
    public LocalDate invoiceDate;
    public BigDecimal totalAmount;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> rawMetadata = new HashMap<>();
    public boolean committed = false;
}
