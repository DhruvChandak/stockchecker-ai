package com.stockpilot.ai.web;

import com.stockpilot.ai.service.ProfitInsightService;
import com.stockpilot.ai.web.dto.ApiDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/insights")
public class InsightsController {
    private final ProfitInsightService profit;

    public InsightsController(ProfitInsightService profit) {
        this.profit = profit;
    }

    @GetMapping("/profit-drop")
    @PreAuthorize("@permissionService.has('insights.profit.view')")
    public ApiDtos.ProfitDropResponse profitDrop(@RequestParam(defaultValue = "current-month") String period) {
        return profit.currentMonth();
    }
}
