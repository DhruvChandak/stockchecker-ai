package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "user_tenant_memberships")
public class UserTenantMembership extends BaseAudit {
    public UUID tenantId;
    public UUID userId;
    public UUID customerId;
    @Enumerated(EnumType.STRING)
    public DomainEnums.Role role = DomainEnums.Role.VIEWER;
}
