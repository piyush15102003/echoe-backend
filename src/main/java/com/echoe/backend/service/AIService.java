package com.echoe.backend.service;

import com.echoe.backend.config.DeepSeekProperties;
import com.echoe.backend.dto.ai.DeepSeekRequest;
import com.echoe.backend.dto.ai.DeepSeekRequest.Message;
import com.echoe.backend.dto.ai.DeepSeekRequest.ResponseFormat;
import com.echoe.backend.dto.ai.DeepSeekResponse;
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
import java.util.List;

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

    public EchoResponse chat(String userMessage, List<ChatMessage> history) {
        DeepSeekRequest request = buildRequest(userMessage, history);

        try {
            DeepSeekResponse response = deepSeekWebClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(DeepSeekResponse.class)
                    .block();

            if (response == null) {
                throw new AIServiceException("Empty response from DeepSeek API");
            }

            String text = response.extractText();
            if (text == null || text.isBlank()) {
                throw new AIServiceException("No text in DeepSeek response");
            }

            return objectMapper.readValue(text, EchoResponse.class);

        } catch (WebClientResponseException ex) {
            log.error("DeepSeek API error: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new AIServiceException("DeepSeek API returned " + ex.getStatusCode(), ex);
        } catch (AIServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AIServiceException("Failed to process DeepSeek response", ex);
        }
    }

    public SummaryResponse generateSummary(List<ChatMessage> conversation) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", summaryPrompt));

        // Build the full conversation as a single user message
        StringBuilder conversationText = new StringBuilder();
        for (ChatMessage msg : conversation) {
            String label = "user".equals(msg.role()) ? "User" : "Echoe";
            conversationText.append(label).append(": ").append(msg.content()).append("\n\n");
        }
        messages.add(new Message("user", conversationText.toString()));

        DeepSeekRequest request = new DeepSeekRequest(
                props.model(),
                messages,
                0.5,
                props.topP(),
                props.maxOutputTokens(),
                ResponseFormat.json()
        );

        try {
            DeepSeekResponse response = deepSeekWebClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(DeepSeekResponse.class)
                    .block();

            if (response == null) {
                throw new AIServiceException("Empty summary response from DeepSeek API");
            }

            String text = response.extractText();
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

    private DeepSeekRequest buildRequest(String userMessage, List<ChatMessage> history) {
        List<Message> messages = new ArrayList<>();

        // System prompt as first message
        messages.add(new Message("system", systemPrompt));

        // Add conversation history (last N messages)
        if (history != null) {
            List<ChatMessage> trimmed = history.size() > MAX_HISTORY_MESSAGES
                    ? history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size())
                    : history;

            for (ChatMessage msg : trimmed) {
                String role = "user".equals(msg.role()) ? "user" : "assistant";
                messages.add(new Message(role, msg.content()));
            }
        }

        // Add current user message
        messages.add(new Message("user", userMessage));

        return new DeepSeekRequest(
                props.model(),
                messages,
                props.temperature(),
                props.topP(),
                props.maxOutputTokens(),
                ResponseFormat.json()
        );
    }
}
