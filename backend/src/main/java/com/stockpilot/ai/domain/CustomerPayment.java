package com.stockpilot.ai.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "customer_payments")
public class CustomerPayment extends TenantOwnedEntity {
    public UUID customerId;
    public UUID salesInvoiceId;
    public BigDecimal amount = BigDecimal.ZERO;
    public LocalDate paymentDate;
    @Enumerated(EnumType.STRING)
    public DomainEnums.PaymentMode mode = DomainEnums.PaymentMode.OTHER;
    public String referenceNumber;
    public UUID sourceImportSessionId;
    public UUID sourceFileId;
    public Integer sourceRowNumber;
    public String sourceFingerprint;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> rawMetadataJson = new LinkedHashMap<>();
    public String notes;
}
