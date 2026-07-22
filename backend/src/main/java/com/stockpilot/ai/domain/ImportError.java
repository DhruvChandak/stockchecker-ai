package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "import_errors")
public class ImportError extends TenantOwnedEntity {
    public UUID importBatchId;
    public int rowNumber;
    public String entityType;
    public String fieldName;
    public String errorCode;
    public String message;
    public String severity = "ERROR";
    public String rawValue;
    public String suggestedFix;
}
