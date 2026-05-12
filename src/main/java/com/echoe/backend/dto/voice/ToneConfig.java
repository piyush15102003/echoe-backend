package com.echoe.backend.dto.voice;

public record ToneConfig(
        double pace,
        double temperature,
        String speaker
) {}
