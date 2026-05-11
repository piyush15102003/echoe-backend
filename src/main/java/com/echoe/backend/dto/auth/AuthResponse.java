package com.echoe.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record AuthResponse(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("access_expires_at") Instant accessExpiresAt,
        @JsonProperty("refresh_expires_at") Instant refreshExpiresAt
) {}
