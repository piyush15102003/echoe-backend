package com.echoe.backend.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EchoResponse(
        String reflection,
        String question,
        @JsonProperty("detected_emotion") String detectedEmotion,
        double intensity,
        @JsonProperty("suggested_tone") String suggestedTone,
        @JsonProperty("crisis_flag") boolean crisisFlag,
        @JsonProperty("session_should_end") boolean sessionShouldEnd
) {}
