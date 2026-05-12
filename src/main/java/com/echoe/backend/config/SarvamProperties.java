package com.echoe.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "echoe.sarvam")
public record SarvamProperties(
        String apiKey,
        String baseUrl,
        String sttModel,
        String ttsModel
) {}
