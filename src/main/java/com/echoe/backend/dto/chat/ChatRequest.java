package com.echoe.backend.dto.chat;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ChatRequest(
        @NotBlank String message,
        String language,
        List<ChatMessage> history
) {}
