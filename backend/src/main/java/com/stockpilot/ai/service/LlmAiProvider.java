package com.stockpilot.ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LlmAiProvider implements AiProvider {
    private final String endpoint;
    private final String apiKey;

    public LlmAiProvider(@Value("${app.ai.llm-endpoint}") String endpoint, @Value("${app.ai.llm-api-key}") String apiKey) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return "optional-llm";
    }

    @Override
    public String complete(String prompt, Map<String, Object> evidence) {
        if (endpoint == null || endpoint.isBlank() || apiKey == null || apiKey.isBlank()) {
            return "LLM provider is not configured. Local evidence is available: " + evidence;
        }
        return "LLM endpoint is configured, but the MVP keeps outbound provider calls behind this interface. Evidence: " + evidence;
    }
}
