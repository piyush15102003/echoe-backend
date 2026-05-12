package com.echoe.backend.dto.vault;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VaultSessionDetail(
        @JsonProperty("session_id") UUID sessionId,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("ended_at") Instant endedAt,
        @JsonProperty("summary_text") String summaryText,
        @JsonProperty("summary_quote") String summaryQuote,
        @JsonProperty("closing_reflection") String closingReflection,
        @JsonProperty("emotion_tags") String[] emotionTags,
        @JsonProperty("crisis_flagged") boolean crisisFlagged,
        @JsonProperty("input_mode") String inputMode,
        List<MessageItem> messages
) {
    public record MessageItem(
            @JsonProperty("message_id") UUID messageId,
            String role,
            String content,
            @JsonProperty("detected_emotion") String detectedEmotion,
            @JsonProperty("emotion_intensity") Double emotionIntensity,
            @JsonProperty("tone_used") String toneUsed,
            @JsonProperty("created_at") Instant createdAt
    ) {}
}
