package com.stockpilot.ai.web;

import com.stockpilot.ai.service.AccountLifecycleService;
import com.stockpilot.ai.web.dto.ApiDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class WorkspaceController {
    private final AccountLifecycleService lifecycleService;

    public WorkspaceController(AccountLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiDtos.AuthResponse create(@Valid @RequestBody ApiDtos.WorkspaceCreateRequest request) {
        return lifecycleService.createWorkspace(request);
    }
}
