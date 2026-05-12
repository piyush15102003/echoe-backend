package com.echoe.backend.service;

import com.echoe.backend.config.DeepSeekProperties;
import com.echoe.backend.dto.ai.DeepSeekRequest;
import com.echoe.backend.dto.ai.DeepSeekRequest.Message;
import com.echoe.backend.dto.ai.DeepSeekResponse;
import com.echoe.backend.dto.intention.IntentionResponse;
import com.echoe.backend.entity.DailyIntentionEntity;
import com.echoe.backend.entity.SessionEntity;
import com.echoe.backend.repository.DailyIntentionRepository;
import com.echoe.backend.repository.SessionRepository;
import com.echoe.backend.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IntentionService {

    private static final Logger log = LoggerFactory.getLogger(IntentionService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DailyIntentionRepository intentionRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final WebClient deepSeekWebClient;
    private final DeepSeekProperties props;

    private String intentionPromptTemplate;

    public IntentionService(DailyIntentionRepository intentionRepository,
                            SessionRepository sessionRepository,
                            UserRepository userRepository,
                            WebClient deepSeekWebClient,
                            DeepSeekProperties props) {
        this.intentionRepository = intentionRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.deepSeekWebClient = deepSeekWebClient;
        this.props = props;
    }

    @PostConstruct
    void loadPrompt() throws IOException {
        intentionPromptTemplate = new ClassPathResource("intention-prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        log.info("Intention prompt loaded ({} chars)", intentionPromptTemplate.length());
    }

    public Optional<IntentionResponse> getTodayIntention(UUID userId) {
        LocalDate today = LocalDate.now(IST);
        return intentionRepository.findByUserIdAndDate(userId, today)
                .map(entity -> new IntentionResponse(
                        entity.getId(),
                        entity.getIntentionText(),
                        entity.getGeneratedFromEmotions() != null
                                ? Arrays.asList(entity.getGeneratedFromEmotions())
                                : List.of()
                ));
    }

    public void markViewed(UUID intentionId, UUID userId) {
        intentionRepository.findById(intentionId).ifPresent(entity -> {
            if (entity.getUserId().equals(userId) && entity.getViewedAt() == null) {
                entity.setViewedAt(Instant.now());
                intentionRepository.save(entity);
            }
        });
    }

    public void generateForAllActiveUsers() {
        LocalDate today = LocalDate.now(IST);
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        List<UUID> activeUserIds = userRepository.findAll().stream()
                .filter(u -> u.getLastActiveAt() != null && u.getLastActiveAt().isAfter(sevenDaysAgo))
                .map(u -> u.getId())
                .toList();

        log.info("Generating daily intentions for {} active users", activeUserIds.size());

        for (UUID userId : activeUserIds) {
            try {
                if (intentionRepository.existsByUserIdAndDate(userId, today)) {
                    continue;
                }
                generateForUser(userId, today);
            } catch (Exception e) {
                log.error("Failed to generate intention for user {}: {}", userId, e.getMessage());
            }
        }
    }

    private void generateForUser(UUID userId, LocalDate date) {
        // Collect emotion tags from last 5 sessions
        List<SessionEntity> sessions = sessionRepository
                .findByUserIdAndEndedAtIsNotNullAndDeletedAtIsNullOrderByEndedAtDesc(userId);

        List<String> emotionTags = sessions.stream()
                .limit(5)
                .filter(s -> s.getEmotionTags() != null)
                .flatMap(s -> Arrays.stream(s.getEmotionTags()))
                .distinct()
                .collect(Collectors.toList());

        if (emotionTags.isEmpty()) {
            emotionTags = List.of("quiet", "present");
        }

        String tagsStr = String.join(", ", emotionTags);
        String prompt = intentionPromptTemplate.replace("{emotion_tags}", tagsStr);

        List<Message> messages = List.of(new Message("user", prompt));

        DeepSeekRequest request = new DeepSeekRequest(
                props.model(),
                messages,
                0.8,
                props.topP(),
                50,
                null
        );

        DeepSeekResponse response = deepSeekWebClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DeepSeekResponse.class)
                .block();

        if (response == null) {
            log.warn("Empty response generating intention for user {}", userId);
            return;
        }

        String intentionText = response.extractText();
        if (intentionText == null || intentionText.isBlank()) {
            log.warn("No text in intention response for user {}", userId);
            return;
        }

        // Clean up: remove quotes and trailing periods if any
        intentionText = intentionText.strip()
                .replaceAll("^\"|\"$", "")
                .replaceAll("^'|'$", "");

        DailyIntentionEntity entity = new DailyIntentionEntity(
                userId,
                date,
                intentionText,
                emotionTags.toArray(new String[0])
        );
        intentionRepository.save(entity);
        log.info("Generated intention for user {}: {}", userId, intentionText);
    }
}
