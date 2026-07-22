package com.stockpilot.ai.service;

import com.stockpilot.ai.domain.DomainEnums;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ErpImportAdapter {
    DomainEnums.SourceType sourceType();
    ImportPreview parse(ImportRequest request);
    ValidationResult validate(ImportPreview preview);
    ImportResult commit(ImportCommitRequest request);

    record ImportRequest(UUID tenantId, UUID batchId, String fileName, InputStream inputStream) {
    }

    record ImportPreview(List<Map<String, String>> rows) {
    }

    record ValidationResult(int validRows, int errorRows, List<String> messages) {
    }

    record ImportCommitRequest(UUID tenantId, UUID batchId) {
    }

    record ImportResult(int committedRows) {
    }
}
