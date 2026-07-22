package com.stockpilot.ai.config;

import com.stockpilot.ai.domain.DomainEnums;

import java.util.UUID;

public final class TenantContext {
    private static final ThreadLocal<UUID> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<DomainEnums.Role> ROLE = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId, UUID userId, DomainEnums.Role role) {
        TENANT_ID.set(tenantId);
        USER_ID.set(userId);
        ROLE.set(role);
    }

    public static void setAccountOnly(UUID userId) {
        TENANT_ID.remove();
        USER_ID.set(userId);
        ROLE.remove();
    }

    public static UUID tenantId() {
        var tenantId = TENANT_ID.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context available");
        }
        return tenantId;
    }

    public static UUID userId() {
        return USER_ID.get();
    }

    public static UUID tenantIdOrNull() {
        return TENANT_ID.get();
    }

    public static DomainEnums.Role role() {
        return ROLE.get();
    }

    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
        ROLE.remove();
    }
}
