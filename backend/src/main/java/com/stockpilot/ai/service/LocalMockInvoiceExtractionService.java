package com.stockpilot.ai.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class LocalMockInvoiceExtractionService implements InvoiceExtractionService {
    @Override
    public Map<String, Object> extract(MultipartFile file) {
        var name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (name.contains("purchase")) {
            return Map.of(
                "invoiceType", "PURCHASE",
                "supplierName", "ABC FMCG Supplier",
                "invoiceDate", LocalDate.now().toString(),
                "confidence", 0.72,
                "items", List.of(Map.of("productName", "Maggi Masala Noodles 70g", "quantity", 12, "rate", new BigDecimal("12.50")))
            );
        }
        return Map.of(
            "invoiceType", "SALES",
            "customerName", "Ravi Traders",
            "invoiceDate", LocalDate.now().toString(),
            "confidence", 0.68,
            "items", List.of(Map.of("productName", "Parle-G 250g", "quantity", 10, "rate", new BigDecimal("28.00")))
        );
    }
}
