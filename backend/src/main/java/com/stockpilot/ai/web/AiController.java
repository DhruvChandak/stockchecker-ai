package com.stockpilot.ai.web;

import com.stockpilot.ai.service.AiAssistantService;
import com.stockpilot.ai.service.ProfitInsightService;
import com.stockpilot.ai.web.dto.ApiDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiAssistantService assistant;
    private final ProfitInsightService profit;

    public AiController(AiAssistantService assistant, ProfitInsightService profit) {
        this.assistant = assistant;
        this.profit = profit;
    }

    @PostMapping("/assistant/chat")
    @PreAuthorize("@permissionService.has('ai.assistant.use')")
    public ApiDtos.ChatResponse chat(@Valid @RequestBody ApiDtos.ChatRequest request) {
        return assistant.chat(request);
    }

    @PostMapping("/assistant/profit-drop")
    @PreAuthorize("@permissionService.has('insights.profit.view')")
    public ApiDtos.ProfitDropResponse profitDrop() {
        return profit.currentMonth();
    }

    @GetMapping("/insights")
    @PreAuthorize("@permissionService.has('ai.assistant.use') and @permissionService.has('dashboard.view') and @permissionService.has('dead_stock.view') and @permissionService.has('reorder.view')")
    public Map<String, Object> insights() {
        return assistant.insights();
    }

    @PostMapping("/insights/generate")
    @PreAuthorize("@permissionService.has('ai.assistant.use') and @permissionService.has('forecast.view') and @permissionService.has('reorder.view') and @permissionService.has('dashboard.view') and @permissionService.has('dead_stock.view')")
    public Map<String, Object> generate() {
        return assistant.generateInsights();
    }
}
