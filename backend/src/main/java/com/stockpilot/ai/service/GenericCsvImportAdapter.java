package com.stockpilot.ai.service;

import com.stockpilot.ai.domain.DomainEnums;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GenericCsvImportAdapter implements ErpImportAdapter {
    @Override
    public DomainEnums.SourceType sourceType() {
        return DomainEnums.SourceType.CSV;
    }

    @Override
    public ImportPreview parse(ImportRequest request) {
        try (var reader = new BufferedReader(new InputStreamReader(request.inputStream(), StandardCharsets.UTF_8))) {
            var lines = reader.lines().filter(line -> !line.isBlank()).toList();
            if (lines.isEmpty()) {
                return new ImportPreview(List.of());
            }
            var headers = uniqueHeaders(splitCsvLine(lines.getFirst()));
            var rows = new ArrayList<Map<String, String>>();
            for (int i = 1; i < lines.size(); i++) {
                var values = splitCsvLine(lines.get(i));
                if (values.stream().allMatch(String::isBlank)) {
                    continue;
                }
                var row = new LinkedHashMap<String, String>();
                for (int c = 0; c < headers.size(); c++) {
                    row.put(headers.get(c), c < values.size() ? values.get(c) : "");
                }
                rows.add(row);
            }
            return new ImportPreview(rows);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not parse CSV file: " + ex.getMessage(), ex);
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

    static List<String> splitCsvLine(String line) {
        var values = new ArrayList<String>();
        var current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    static List<String> uniqueHeaders(List<String> headers) {
        var seen = new LinkedHashMap<String, Integer>();
        var result = new ArrayList<String>();
        for (var header : headers) {
            var trimmed = header == null ? "" : header.trim();
            var key = trimmed.toLowerCase(java.util.Locale.ROOT);
            var count = seen.merge(key, 1, Integer::sum);
            result.add(count == 1 ? trimmed : trimmed + "_" + count);
        }
        return result;
    }
}
