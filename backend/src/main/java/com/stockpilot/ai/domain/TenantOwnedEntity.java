package com.stockpilot.ai.domain;

import jakarta.persistence.MappedSuperclass;

import java.util.UUID;

@MappedSuperclass
public abstract class TenantOwnedEntity extends BaseAudit {
    public UUID tenantId;
}
