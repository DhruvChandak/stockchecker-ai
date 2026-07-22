package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payment_reminders")
public class PaymentReminder extends TenantOwnedEntity {
    public UUID customerId;
    public BigDecimal amountDue = BigDecimal.ZERO;
    public LocalDate reminderDate;
    public String reminderTime;
    public String status = "OPEN";
    public String notes;
    public Instant completedAt;
    public UUID completedPaymentId;
}
