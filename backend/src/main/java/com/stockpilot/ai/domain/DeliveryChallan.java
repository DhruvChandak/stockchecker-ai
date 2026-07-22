package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "delivery_challans")
public class DeliveryChallan extends TenantOwnedEntity {
    public UUID customerId;
    public UUID salesInvoiceId;
    public String challanNumber;
    public String status = "OPEN";
}
