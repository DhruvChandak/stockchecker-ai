package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "staging_suppliers")
public class StagingSupplier extends TenantOwnedEntity {
    public UUID importBatchId;
    public int rowNumber;
    public String name;
    public String phone;
    public String email;
    public String gstin;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> rawMetadata = new HashMap<>();
    public boolean committed = false;
}
