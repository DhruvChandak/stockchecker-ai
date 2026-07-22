package com.stockpilot.ai.web;

import com.stockpilot.ai.service.TallyIntegrationService;
import com.stockpilot.ai.web.dto.ApiDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {
    private final TallyIntegrationService tally;

    public IntegrationController(TallyIntegrationService tally) {
        this.tally = tally;
    }

    @GetMapping("/tally/status")
    @PreAuthorize("@permissionService.has('integrations.tally.view')")
    public ApiDtos.TallyIntegrationStatus tallyStatus() {
        return tally.status();
    }
}
