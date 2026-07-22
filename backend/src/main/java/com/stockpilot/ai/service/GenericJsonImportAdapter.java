package com.stockpilot.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.ai.domain.DomainEnums;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GenericJsonImportAdapter implements ErpImportAdapter {
    private final ObjectMapper objectMapper;

    public GenericJsonImportAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public DomainEnums.SourceType sourceType() {
        return DomainEnums.SourceType.JSON;
    }

    @Override
    public ImportPreview parse(ImportRequest request) {
        try {
            var raw = objectMapper.readValue(request.inputStream(), new TypeReference<Object>() {});
            var rows = new ArrayList<Map<String, String>>();
            if (raw instanceof List<?> list) {
                for (var item : list) {
                    rows.add(flatten(item));
                }
            } else if (raw instanceof Map<?, ?> map && map.get("rows") instanceof List<?> list) {
                for (var item : list) {
                    rows.add(flatten(item));
                }
            } else {
                rows.add(flatten(raw));
            }
            return new ImportPreview(rows);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not parse JSON file: " + ex.getMessage(), ex);
        }
    }

    @Override
    public ValidationResult validate(ImportPreview preview) {
        return new ValidationResult(preview.rows().size(), 0, List.of());
    }

    @Override
    public ImportResult commit(ImportCommitRequest request) {
        return new ImportResult(0);
    }

    private Map<String, String> flatten(Object item) {
        var row = new LinkedHashMap<String, String>();
        if (item instanceof Map<?, ?> map) {
            map.forEach((key, value) -> row.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
        }
        return row;
    }
}
