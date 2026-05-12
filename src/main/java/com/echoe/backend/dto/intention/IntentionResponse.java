package com.echoe.backend.dto.intention;

import java.util.List;
import java.util.UUID;

public record IntentionResponse(
        UUID id,
        String text,
        List<String> generatedFrom
) {}
