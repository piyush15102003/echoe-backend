package com.echoe.backend.dto.vault;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record VaultSessionSummary(
        @JsonProperty("session_id") UUID sessionId,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("ended_at") Instant endedAt,
        @JsonProperty("summary_text") String summaryText,
        @JsonProperty("emotion_tags") String[] emotionTags,
        @JsonProperty("crisis_flagged") boolean crisisFlagged,
        @JsonProperty("input_mode") String inputMode
) {}
