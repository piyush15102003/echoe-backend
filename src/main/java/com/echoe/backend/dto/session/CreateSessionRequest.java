package com.echoe.backend.dto.session;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateSessionRequest(
        @JsonProperty("input_mode") String inputMode,
        String language
) {
    public CreateSessionRequest {
        if (inputMode == null) inputMode = "text";
        if (language == null) language = "en";
    }
}
