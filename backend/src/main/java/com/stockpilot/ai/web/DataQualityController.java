package com.stockpilot.ai.web;

import com.stockpilot.ai.service.DataQualityService;
import com.stockpilot.ai.web.dto.ApiDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/data-quality")
public class DataQualityController {
    private final DataQualityService dataQuality;

    public DataQualityController(DataQualityService dataQuality) {
        this.dataQuality = dataQuality;
    }

    @GetMapping("/summary")
    @PreAuthorize("@permissionService.has('data_quality.view')")
    public ApiDtos.DataQualitySummary summary() {
        return dataQuality.summary();
    }

    @GetMapping("/products/duplicates")
    @PreAuthorize("@permissionService.has('data_quality.view')")
    public List<ApiDtos.DuplicateProductGroup> duplicates() {
        return dataQuality.duplicates();
    }

    @GetMapping("/products/missing-fields")
    @PreAuthorize("@permissionService.has('data_quality.view')")
    public List<ApiDtos.MissingProductFields> missingFields() {
        return dataQuality.missingFields();
    }

    @PostMapping("/products/{id}/apply-suggestion")
    @PreAuthorize("@permissionService.has('data_quality.apply_suggestion')")
    public ApiDtos.ProductResponse apply(@PathVariable UUID id, @Valid @RequestBody ApiDtos.ApplyProductSuggestionRequest request) {
        return dataQuality.applySuggestion(id, request);
    }

    @PostMapping("/products/merge")
    @PreAuthorize("@permissionService.has('data_quality.merge')")
    public Map<String, Object> merge(@Valid @RequestBody ApiDtos.MergeProductsRequest request) {
        return dataQuality.merge(request);
    }
}
