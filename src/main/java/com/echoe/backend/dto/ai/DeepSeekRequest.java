package com.echoe.backend.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DeepSeekRequest(
        String model,
        List<Message> messages,
        double temperature,
        @JsonProperty("top_p") double topP,
        @JsonProperty("max_tokens") int maxTokens,
        @JsonProperty("response_format") ResponseFormat responseFormat
) {

    public record Message(String role, String content) {}

    public record ResponseFormat(String type) {
        public static ResponseFormat json() {
            return new ResponseFormat("json_object");
        }
    }
}
