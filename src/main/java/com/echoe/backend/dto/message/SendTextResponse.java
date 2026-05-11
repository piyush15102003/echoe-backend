package com.echoe.backend.dto.message;

import com.echoe.backend.dto.ai.EchoResponse;
import com.echoe.backend.dto.safety.CrisisResource;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record SendTextResponse(
        @JsonProperty("message_id") UUID messageId,
        EchoResponse echo,
        @JsonProperty("crisis_detected") boolean crisisDetected,
        @JsonProperty("crisis_resources") List<CrisisResource> crisisResources,
        @JsonProperty("session_continues") boolean sessionContinues
) {}
