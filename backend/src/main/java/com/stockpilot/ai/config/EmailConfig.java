package com.stockpilot.ai.config;

import com.stockpilot.ai.service.EmailService;
import com.stockpilot.ai.service.LocalLoggingEmailService;
import com.stockpilot.ai.service.ResendEmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailConfig {
    @Bean
    EmailService emailService(
        @Value("${app.email.provider:local}") String provider,
        @Value("${app.email.resend-api-key:}") String resendApiKey,
        @Value("${app.email.from:StockPilot AI <noreply@example.com>}") String from
    ) {
        if ("resend".equalsIgnoreCase(provider)) {
            return new ResendEmailService(resendApiKey, from);
        }
        return new LocalLoggingEmailService();
    }
}
