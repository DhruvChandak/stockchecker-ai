package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_record_mappings")
public class ExternalRecordMapping extends TenantOwnedEntity {
    public String sourceSystem;
    public UUID importSessionId;
    public UUID sourceFileId;
    public String entityType;
    public String externalId;
    public String normalizedKey;
    public String localEntityType;
    public UUID localEntityId;
    public String sourceHash;
    public Instant lastSeenAt;
}
