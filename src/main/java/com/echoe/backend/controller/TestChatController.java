package com.echoe.backend.controller;

import com.echoe.backend.dto.ai.EchoResponse;
import com.echoe.backend.dto.chat.ChatRequest;
import com.echoe.backend.dto.chat.ChatResponse;
import com.echoe.backend.dto.safety.CrisisResource;
import com.echoe.backend.dto.safety.SafetyResult;
import com.echoe.backend.service.AIService;
import com.echoe.backend.service.CrisisResourceProvider;
import com.echoe.backend.service.SafetyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/test")
public class TestChatController {

    private final SafetyService safetyService;
    private final AIService aiService;
    private final CrisisResourceProvider crisisResourceProvider;

    public TestChatController(SafetyService safetyService,
                              AIService aiService,
                              CrisisResourceProvider crisisResourceProvider) {
        this.safetyService = safetyService;
        this.aiService = aiService;
        this.crisisResourceProvider = crisisResourceProvider;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        // 1. Check for crisis keywords in user message
        SafetyResult safetyResult = safetyService.check(request.message());

        // 2. Call Gemini via AIService
        EchoResponse echoResponse = aiService.chat(request.message(), request.history());

        // 3. Defense in depth — either layer triggers crisis resources
        boolean crisisDetected = safetyResult.crisis() || echoResponse.crisisFlag();

        List<CrisisResource> resources = crisisDetected
                ? crisisResourceProvider.getResources()
                : List.of();

        return new ChatResponse(echoResponse, crisisDetected, resources);
    }
}
