package com.echoe.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AnonymousAuthRequest(
        @NotNull @JsonProperty("device_id") UUID deviceId,
        @JsonProperty("preferred_language") String preferredLanguage,
        @JsonProperty("voice_preference") String voicePreference
) {
    public AnonymousAuthRequest {
        if (preferredLanguage == null) preferredLanguage = "en";
        if (voicePreference == null) voicePreference = "female";
    }
}
