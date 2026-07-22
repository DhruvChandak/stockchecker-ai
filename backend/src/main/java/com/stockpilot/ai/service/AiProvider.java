package com.stockpilot.ai.service;

import java.util.Map;

public interface AiProvider {
    String name();
    String complete(String prompt, Map<String, Object> evidence);
}
