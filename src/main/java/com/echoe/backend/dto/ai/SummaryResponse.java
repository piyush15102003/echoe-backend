package com.echoe.backend.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SummaryResponse(
        @JsonProperty("summary_text") String summaryText,
        @JsonProperty("summary_quote") String summaryQuote,
        @JsonProperty("closing_reflection") String closingReflection,
        @JsonProperty("emotion_tags") List<String> emotionTags
) {}
