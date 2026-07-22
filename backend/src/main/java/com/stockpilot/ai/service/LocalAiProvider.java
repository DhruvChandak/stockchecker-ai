package com.stockpilot.ai.service;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LocalAiProvider implements AiProvider {
    @Override
    public String name() {
        return "local-rule-based";
    }

    @Override
    public String complete(String prompt, Map<String, Object> evidence) {
        return "Based on the available StockPilot data: " + evidence;
    }
}
