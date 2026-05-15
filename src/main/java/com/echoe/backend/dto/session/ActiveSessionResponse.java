package com.echoe.backend.dto.session;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record ActiveSessionResponse(
        @JsonProperty("session_id") UUID sessionId,
        @JsonProperty("input_mode") String inputMode,
        @JsonProperty("started_at") Instant startedAt,
        boolean paused
) {}
