package com.echoe.backend.dto.ai;

import java.util.List;

public record GeminiRequest(
        List<Content> contents,
        SystemInstruction systemInstruction,
        GenerationConfig generationConfig
) {

    public record Content(String role, List<Part> parts) {}

    public record Part(String text) {}

    public record SystemInstruction(List<Part> parts) {
        public static SystemInstruction of(String text) {
            return new SystemInstruction(List.of(new Part(text)));
        }
    }

    public record GenerationConfig(
            double temperature,
            double topP,
            int maxOutputTokens,
            String responseMimeType
    ) {}
}
