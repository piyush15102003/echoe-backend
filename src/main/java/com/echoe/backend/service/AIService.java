package com.echoe.backend.service;

import com.echoe.backend.config.GeminiProperties;
import com.echoe.backend.dto.ai.EchoResponse;
import com.echoe.backend.dto.ai.GeminiRequest;
import com.echoe.backend.dto.ai.GeminiRequest.Content;
import com.echoe.backend.dto.ai.GeminiRequest.GenerationConfig;
import com.echoe.backend.dto.ai.GeminiRequest.Part;
import com.echoe.backend.dto.ai.GeminiRequest.SystemInstruction;
import com.echoe.backend.dto.ai.GeminiResponse;
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

    private final WebClient geminiWebClient;
    private final GeminiProperties props;
    private final ObjectMapper objectMapper;

    private String systemPrompt;

    public AIService(WebClient geminiWebClient, GeminiProperties props, ObjectMapper objectMapper) {
        this.geminiWebClient = geminiWebClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadSystemPrompt() throws IOException {
        systemPrompt = new ClassPathResource("system-prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        log.info("System prompt loaded ({} chars)", systemPrompt.length());
    }

    public EchoResponse chat(String userMessage, List<ChatMessage> history) {
        GeminiRequest request = buildRequest(userMessage, history);

        String uri = "/v1beta/models/" + props.model() + ":generateContent?key=" + props.apiKey();

        try {
            GeminiResponse response = geminiWebClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .block();

            if (response == null) {
                throw new AIServiceException("Empty response from Gemini API");
            }

            String text = response.extractText();
            if (text == null || text.isBlank()) {
                throw new AIServiceException("No text in Gemini response");
            }

            return objectMapper.readValue(text, EchoResponse.class);

        } catch (WebClientResponseException ex) {
            log.error("Gemini API error: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new AIServiceException("Gemini API returned " + ex.getStatusCode(), ex);
        } catch (AIServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AIServiceException("Failed to process Gemini response", ex);
        }
    }

    private GeminiRequest buildRequest(String userMessage, List<ChatMessage> history) {
        List<Content> contents = new ArrayList<>();

        // Add conversation history (last N messages)
        if (history != null) {
            List<ChatMessage> trimmed = history.size() > MAX_HISTORY_MESSAGES
                    ? history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size())
                    : history;

            for (ChatMessage msg : trimmed) {
                String role = "user".equals(msg.role()) ? "user" : "model";
                contents.add(new Content(role, List.of(new Part(msg.content()))));
            }
        }

        // Add current user message
        contents.add(new Content("user", List.of(new Part(userMessage))));

        GenerationConfig genConfig = new GenerationConfig(
                props.temperature(),
                props.topP(),
                props.maxOutputTokens(),
                "application/json"
        );

        return new GeminiRequest(
                contents,
                SystemInstruction.of(systemPrompt),
                genConfig
        );
    }
}
