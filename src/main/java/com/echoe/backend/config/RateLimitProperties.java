package com.echoe.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "echoe.rate-limit")
public record RateLimitProperties(
        int requestsPerMinute,
        int freeSessionsPerWeek,
        int messagesPerSession,
        int authRequestsPerMinute
) {
    public RateLimitProperties {
        if (requestsPerMinute <= 0) requestsPerMinute = 60;
        if (freeSessionsPerWeek <= 0) freeSessionsPerWeek = 5;
        if (messagesPerSession <= 0) messagesPerSession = 50;
        if (authRequestsPerMinute <= 0) authRequestsPerMinute = 10;
    }
}
