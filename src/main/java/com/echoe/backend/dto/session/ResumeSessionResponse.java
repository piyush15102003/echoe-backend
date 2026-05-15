package com.echoe.backend.dto.session;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResumeSessionResponse(
        @JsonProperty("session_id") UUID sessionId,
        @JsonProperty("input_mode") String inputMode,
        List<MessageItem> messages
) {
    public record MessageItem(
            String role,
            String content,
            @JsonProperty("created_at") Instant createdAt
    ) {}
}
