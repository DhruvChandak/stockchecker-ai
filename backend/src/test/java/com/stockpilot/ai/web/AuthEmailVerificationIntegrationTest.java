package com.stockpilot.ai.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.ai.config.JwtService;
import com.stockpilot.ai.domain.UserAccount;
import com.stockpilot.ai.repo.Repositories;
import com.stockpilot.ai.service.AuthTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.auth.require-email-verification=true",
    "app.auth.email-cooldown-seconds=60"
})
class AuthEmailVerificationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired Repositories.UserRepository users;
    @Autowired Repositories.MembershipRepository memberships;
    @Autowired JwtService jwtService;
    @Autowired AuthTokenService tokenService;

    @Test
    void registerCreatesUnverifiedUserAndLoginIsBlockedUntilVerification() throws Exception {
        var email = "verify-" + System.nanoTime() + "@example.com";
        var response = mapper.readTree(register(email));

        assertThat(response.get("emailVerificationRequired").asBoolean()).isTrue();
        assertThat(response.has("accessToken")).isFalse();
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.emailVerified).isFalse();
        assertThat(user.verificationTokenHash).isNotBlank();

        var blocked = mapper.readTree(mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString());
        assertThat(blocked.get("error").asText()).isEqualTo("EMAIL_NOT_VERIFIED");

        var rawToken = "manual-verification-token";
        user.verificationTokenHash = tokenService.hash(rawToken);
        user.verificationTokenExpiresAt = Instant.now().plusSeconds(3600);
        users.save(user);

        mvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(rawToken)))
            .andExpect(status().isOk());

        var verified = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(verified.emailVerified).isTrue();
        assertThat(verified.verificationTokenHash).isNull();

        var login = mapper.readTree(mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        assertThat(login.get("accessToken").asText()).isNotBlank();
    }

    @Test
    void invalidAndExpiredVerificationTokensFailSafely() throws Exception {
        mvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"missing\"}"))
            .andExpect(status().isBadRequest());

        var email = "expired-" + System.nanoTime() + "@example.com";
        register(email);
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        user.verificationTokenHash = tokenService.hash("expired-token");
        user.verificationTokenExpiresAt = Instant.now().minusSeconds(1);
        users.save(user);

        mvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"expired-token\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void forgotAndResetPasswordUseHashedSingleUseToken() throws Exception {
        var email = "reset-" + System.nanoTime() + "@example.com";
        register(email);
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        user.emailVerified = true;
        users.save(user);

        mvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)))
            .andExpect(status().isOk());
        user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.passwordResetTokenHash).isNotBlank();
        assertThat(user.passwordResetTokenHash).doesNotContain("manual-reset-token");

        var rawToken = "manual-reset-token";
        user.passwordResetTokenHash = tokenService.hash(rawToken);
        user.passwordResetTokenExpiresAt = Instant.now().plusSeconds(1800);
        users.save(user);

        mvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"newPassword\":\"newPassword123\"}".formatted(rawToken)))
            .andExpect(status().isOk());

        var updated = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(updated.passwordResetTokenHash).isNull();
        assertThat(updated.passwordResetUsedAt).isNotNull();

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"newPassword123\"}".formatted(email)))
            .andExpect(status().isOk());
    }

    @Test
    void unverifiedUserCannotUseStaleTokenForBusinessApis() throws Exception {
        var email = "stale-token-" + System.nanoTime() + "@example.com";
        register(email);
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        var membership = memberships.findFirstByUserId(user.id).orElseThrow();
        var token = jwtService.issue(user.id, membership.tenantId, user.email, membership.role);

        mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void publicGenericResponsesDoNotRevealUnknownEmail() throws Exception {
        mvc.perform(post("/api/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown@example.com\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown@example.com\"}"))
            .andExpect(status().isOk());
    }

    private String register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"businessName":"Verification Tenant","businessMode":"HYBRID","email":"%s","password":"password123","fullName":"Owner"}
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }
}
