package com.stockpilot.ai.web;

import com.stockpilot.ai.service.AccountLifecycleService;
import com.stockpilot.ai.web.dto.ApiDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountLifecycleService lifecycleService;

    public AccountController(AccountLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ApiDtos.AccountDeletionResponse requestDeletion(@Valid @RequestBody ApiDtos.DestructiveActionRequest request) {
        return lifecycleService.deleteMyAccount(request.confirmation());
    }
}
