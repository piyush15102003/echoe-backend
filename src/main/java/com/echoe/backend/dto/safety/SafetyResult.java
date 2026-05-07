package com.echoe.backend.dto.safety;

public record SafetyResult(boolean crisis, String matchedPattern) {

    public static SafetyResult safe() {
        return new SafetyResult(false, null);
    }

    public static SafetyResult crisis(String pattern) {
        return new SafetyResult(true, pattern);
    }
}
