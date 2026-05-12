package com.echoe.backend.service;

import com.echoe.backend.dto.voice.ToneConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ToneEngine {

    private static final Map<String, double[]> TONE_MAP = Map.of(
            "soft_slow",        new double[]{0.80, 0.3},
            "grounded_soften",  new double[]{1.05, 0.7},
            "calm_steady",      new double[]{0.85, 0.4},
            "warm_curious",     new double[]{0.95, 0.6},
            "gentle_light",     new double[]{0.95, 0.8},
            "anchored_direct",  new double[]{0.85, 0.5}
    );

    private static final double DEFAULT_PACE = 0.90;
    private static final double DEFAULT_TEMPERATURE = 0.5;

    public ToneConfig resolve(String suggestedTone, String voicePreference) {
        double[] params = TONE_MAP.getOrDefault(suggestedTone,
                new double[]{DEFAULT_PACE, DEFAULT_TEMPERATURE});

        String speaker = "male".equalsIgnoreCase(voicePreference) ? "shubh" : "ritu";

        return new ToneConfig(params[0], params[1], speaker);
    }
}
