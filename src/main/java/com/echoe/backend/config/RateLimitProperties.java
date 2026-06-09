package com.echoe.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "echoe.rate-limit")
public record RateLimitProperties(
        int requestsPerMinute,
        int freeSessionsPerDay,
        int messagesPerSession,
        int authRequestsPerMinute
) {
    public RateLimitProperties {
        if (requestsPerMinute <= 0) requestsPerMinute = 60;
        if (freeSessionsPerDay <= 0) freeSessionsPerDay = 2;
        if (messagesPerSession <= 0) messagesPerSession = 50;
        if (authRequestsPerMinute <= 0) authRequestsPerMinute = 10;
    }
}
