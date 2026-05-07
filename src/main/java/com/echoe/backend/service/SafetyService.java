package com.echoe.backend.service;

import com.echoe.backend.dto.safety.SafetyResult;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class SafetyService {

    private static final Logger log = LoggerFactory.getLogger(SafetyService.class);

    private List<Pattern> crisisPatterns;

    @PostConstruct
    void loadKeywords() throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("crisis-keywords.txt").getInputStream(),
                StandardCharsets.UTF_8))) {

            crisisPatterns = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(keyword -> {
                        String quoted = Pattern.quote(keyword);
                        // \b word boundaries don't work with Devanagari/non-Latin scripts
                        boolean isAscii = keyword.chars().allMatch(c -> c < 128);
                        String regex = isAscii
                                ? "(?i)(?u)\\b" + quoted + "\\b"
                                : "(?i)(?u)" + quoted;
                        return Pattern.compile(regex);
                    })
                    .toList();
        }
        // Never log the actual keywords — just the count
        log.info("Loaded {} crisis detection patterns", crisisPatterns.size());
    }

    public SafetyResult check(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return SafetyResult.safe();
        }

        String normalized = userMessage.toLowerCase().trim();

        for (Pattern pattern : crisisPatterns) {
            if (pattern.matcher(normalized).find()) {
                // Never log the user's actual message
                log.warn("Crisis keyword detected in user message");
                return SafetyResult.crisis(pattern.pattern());
            }
        }
        return SafetyResult.safe();
    }
}
