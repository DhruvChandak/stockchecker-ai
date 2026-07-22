package com.stockpilot.ai.web;

import com.stockpilot.ai.service.ForecastService;
import com.stockpilot.ai.web.dto.ApiDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/forecast")
public class ForecastController {
    private final ForecastService forecasts;

    public ForecastController(ForecastService forecasts) {
        this.forecasts = forecasts;
    }

    @PostMapping("/run")
    @PreAuthorize("@permissionService.has('forecast.view')")
    public List<ApiDtos.ForecastResponse> run() {
        return forecasts.run();
    }

    @GetMapping("/results")
    @PreAuthorize("@permissionService.has('forecast.view')")
    public List<ApiDtos.ForecastResponse> results() {
        return forecasts.results();
    }

    @GetMapping("/reorder-suggestions")
    @PreAuthorize("@permissionService.has('reorder.view')")
    public List<ApiDtos.ReorderSuggestionResponse> suggestions() {
        return forecasts.suggestions();
    }
}
