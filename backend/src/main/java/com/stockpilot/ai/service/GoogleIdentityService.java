package com.stockpilot.ai.service;

public interface GoogleIdentityService {
    GoogleUserInfo verify(String idToken);

    record GoogleUserInfo(String subject, String email, String name) {
    }
}
