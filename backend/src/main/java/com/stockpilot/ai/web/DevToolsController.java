package com.stockpilot.ai.web;

import com.stockpilot.ai.service.DevToolsService;
import com.stockpilot.ai.web.dto.ApiDtos;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dev")
@Profile({"dev", "test"})
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevToolsController {
    private final DevToolsService devTools;

    public DevToolsController(DevToolsService devTools) {
        this.devTools = devTools;
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('OWNER')")
    public Map<String, Object> status() {
        return Map.of("enabled", true);
    }

    @PostMapping("/current-tenant/reset-business-data")
    @PreAuthorize("hasRole('OWNER')")
    public Map<String, Object> resetCurrentTenantBusinessData(@Valid @RequestBody ApiDtos.DevResetRequest request) {
        return devTools.resetCurrentTenantBusinessData(request.confirmation());
    }
}
