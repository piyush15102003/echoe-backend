package com.echoe.backend.dto.session;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record PauseSessionResponse(
        @JsonProperty("session_id") UUID sessionId,
        @JsonProperty("paused_at") Instant pausedAt
) {}
