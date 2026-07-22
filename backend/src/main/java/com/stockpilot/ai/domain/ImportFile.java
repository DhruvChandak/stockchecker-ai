package com.stockpilot.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "import_files")
public class ImportFile extends TenantOwnedEntity {
    public UUID importBatchId;
    public String fileName;
    public String contentType;
    public String storageKey;
    public long sizeBytes;
}
