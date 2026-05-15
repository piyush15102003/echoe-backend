package com.echoe.backend.service;

import com.echoe.backend.config.DeepSeekProperties;
import com.echoe.backend.dto.ai.EchoResponse;
import com.echoe.backend.dto.ai.SummaryResponse;
import com.echoe.backend.dto.chat.ChatMessage;
import com.echoe.backend.exception.AIServiceException;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);
    private static final int MAX_HISTORY_MESSAGES = 10;

    private final WebClient deepSeekWebClient;
    private final DeepSeekProperties props;
    private final ObjectMapper objectMapper;

    private String systemPrompt;
    private String summaryPrompt;

    public AIService(WebClient deepSeekWebClient, DeepSeekProperties props, ObjectMapper objectMapper) {
        this.deepSeekWebClient = deepSeekWebClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadPrompts() throws IOException {
        systemPrompt = new ClassPathResource("system-prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        log.info("System prompt loaded ({} chars)", systemPrompt.length());

        summaryPrompt = new ClassPathResource("summary-prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        log.info("Summary prompt loaded ({} chars)", summaryPrompt.length());
    }

    private static final int MAX_RETRIES = 2;

    public EchoResponse chat(String userMessage, List<ChatMessage> history) {
        Map<String, Object> request = buildRequest(userMessage, history, props.temperature());

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = deepSeekWebClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                String text = extractText(response);
                log.debug("DeepSeek extracted text (attempt {}): {}", attempt + 1, text);

                if (text == null || text.isBlank()) {
                    if (attempt < MAX_RETRIES) {
                        log.warn("DeepSeek returned blank text (attempt {}), retrying...", attempt + 1);
                        continue;
                    }
                    log.error("DeepSeek returned empty text after {} attempts. Full response: {}",
                            MAX_RETRIES + 1, response);
                    return fallbackResponse();
                }

                return objectMapper.readValue(text, EchoResponse.class);

            } catch (WebClientResponseException ex) {
                log.error("DeepSeek API error: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                throw new AIServiceException("DeepSeek API returned " + ex.getStatusCode(), ex);
            } catch (AIServiceException ex) {
                throw ex;
            } catch (Exception ex) {
                log.error("Failed to process DeepSeek response", ex);
                throw new AIServiceException("Failed to process DeepSeek response", ex);
            }
        }
        return fallbackResponse();
    }

    private EchoResponse fallbackResponse() {
        return new EchoResponse(
                "I'm here with you. Take your time.",
                "Can you tell me a little more about what you're feeling?",
                "other", 0.3, "warm_curious", false, false
        );
    }

    public SummaryResponse generateSummary(List<ChatMessage> conversation) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", summaryPrompt));

        StringBuilder conversationText = new StringBuilder();
        for (ChatMessage msg : conversation) {
            String label = "user".equals(msg.role()) ? "User" : "Echoe";
            conversationText.append(label).append(": ").append(msg.content()).append("\n\n");
        }
        messages.add(Map.of("role", "user", "content", conversationText.toString()));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", props.model());
        request.put("messages", messages);
        request.put("temperature", 0.5);
        request.put("top_p", props.topP());
        request.put("max_tokens", props.maxOutputTokens());
        request.put("response_format", Map.of("type", "json_object"));

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = deepSeekWebClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String text = extractText(response);
            if (text == null || text.isBlank()) {
                throw new AIServiceException("No text in DeepSeek summary response");
            }

            return objectMapper.readValue(text, SummaryResponse.class);

        } catch (WebClientResponseException ex) {
            log.error("DeepSeek summary API error: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new AIServiceException("DeepSeek API returned " + ex.getStatusCode(), ex);
        } catch (AIServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AIServiceException("Failed to generate summary", ex);
        }
    }

    private Map<String, Object> buildRequest(String userMessage, List<ChatMessage> history,
                                              double temperature) {
        List<Map<String, String>> messages = new ArrayList<>();

        messages.add(Map.of("role", "system", "content", systemPrompt));

        if (history != null) {
            List<ChatMessage> trimmed = history.size() > MAX_HISTORY_MESSAGES
                    ? history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size())
                    : history;

            for (ChatMessage msg : trimmed) {
                String role = "user".equals(msg.role()) ? "user" : "assistant";
                messages.add(Map.of("role", role, "content", msg.content()));
            }
        }

        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", props.model());
        request.put("messages", messages);
        request.put("temperature", temperature);
        request.put("top_p", props.topP());
        request.put("max_tokens", props.maxOutputTokens());
        request.put("response_format", Map.of("type", "json_object"));

        return request;
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
        if (message == null) {
            return null;
        }
        return (String) message.get("content");
    }
}
