package com.echoe.backend.dto.session;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record CreateSessionResponse(
        @JsonProperty("session_id") UUID sessionId,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("opening_message") String openingMessage
) {}
