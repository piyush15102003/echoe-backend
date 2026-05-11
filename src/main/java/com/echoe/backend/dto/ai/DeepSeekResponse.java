package com.echoe.backend.dto.ai;

import java.util.List;

public record DeepSeekResponse(List<Choice> choices) {

    public record Choice(Message message) {}

    public record Message(String role, String content) {}

    public String extractText() {
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        Choice choice = choices.getFirst();
        if (choice.message() == null || choice.message().content() == null) {
            return "";
        }
        return choice.message().content();
    }
}
