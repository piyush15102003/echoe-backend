package com.echoe.backend.dto.session;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record EndSessionResponse(
        @JsonProperty("session_id") UUID sessionId,
        @JsonProperty("ended_at") Instant endedAt,
        String status
) {}
