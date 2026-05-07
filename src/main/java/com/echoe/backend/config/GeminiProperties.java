package com.echoe.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "echoe.gemini")
public record GeminiProperties(
        String apiKey,
        String model,
        String baseUrl,
        double temperature,
        double topP,
        int maxOutputTokens
) {}
