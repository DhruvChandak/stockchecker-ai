package com.stockpilot.ai.service;

public interface EmailService {
    void sendVerificationEmail(String email, String verificationLink);
    void sendPasswordResetEmail(String email, String resetLink);
}
