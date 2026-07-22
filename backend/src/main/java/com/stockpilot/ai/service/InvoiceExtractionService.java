package com.stockpilot.ai.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface InvoiceExtractionService {
    Map<String, Object> extract(MultipartFile file);
}
