package com.echoe.backend.dto.voice;

import com.echoe.backend.dto.ai.EchoResponse;
import com.echoe.backend.dto.safety.CrisisResource;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record SendVoiceResponse(
        @JsonProperty("message_id") UUID messageId,
        @JsonProperty("transcribed_text") String transcribedText,
        EchoResponse echo,
        @JsonProperty("audio_base64") String audioBase64,
        @JsonProperty("crisis_detected") boolean crisisDetected,
        @JsonProperty("crisis_resources") List<CrisisResource> crisisResources,
        @JsonProperty("session_continues") boolean sessionContinues
) {}
