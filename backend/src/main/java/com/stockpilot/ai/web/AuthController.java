package com.stockpilot.ai.web;

import com.stockpilot.ai.config.TenantContext;
import com.stockpilot.ai.repo.Repositories;
import com.stockpilot.ai.service.AuthService;
import com.stockpilot.ai.service.PermissionService;
import com.stockpilot.ai.web.dto.ApiDtos;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final Repositories.UserRepository users;
    private final PermissionService permissionService;

    public AuthController(AuthService authService, Repositories.UserRepository users, PermissionService permissionService) {
        this.authService = authService;
        this.users = users;
        this.permissionService = permissionService;
    }

    @PostMapping("/register")
    public Object register(@Valid @RequestBody ApiDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public ApiDtos.AuthResponse login(@Valid @RequestBody ApiDtos.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/google")
    public ApiDtos.AuthResponse google(@Valid @RequestBody ApiDtos.GoogleLoginRequest request) {
        return authService.loginWithGoogle(request);
    }

    @PostMapping("/verify-email")
    public ApiDtos.AuthMessageResponse verifyEmail(@Valid @RequestBody ApiDtos.VerifyEmailRequest request) {
        return authService.verifyEmail(request);
    }

    @PostMapping("/resend-verification")
    public ApiDtos.AuthMessageResponse resendVerification(@Valid @RequestBody ApiDtos.ResendVerificationRequest request) {
        return authService.resendVerification(request);
    }

    @PostMapping("/forgot-password")
    public ApiDtos.AuthMessageResponse forgotPassword(@Valid @RequestBody ApiDtos.ForgotPasswordRequest request) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public ApiDtos.AuthMessageResponse resetPassword(@Valid @RequestBody ApiDtos.ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @PostMapping("/refresh")
    public ApiDtos.AuthResponse refresh(Authentication authentication) {
        return authService.refresh(authentication.getName());
    }

    @GetMapping("/me")
    public ApiDtos.MeResponse me(Authentication authentication) {
        var user = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        return new ApiDtos.MeResponse(user.id, user.email, user.fullName, TenantContext.tenantIdOrNull(), TenantContext.role());
    }

    @GetMapping("/me/permissions")
    public Map<String, Object> permissions() {
        if (TenantContext.role() == null) {
            return Map.of("role", "WORKSPACELESS", "permissions", List.of());
        }
        return permissionService.currentPermissionResponse();
    }

    @GetMapping("/me/tenants")
    public List<Map<String, Object>> tenants(Authentication authentication) {
        var user = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        return permissionService.accessibleTenants(user.id);
    }
}
