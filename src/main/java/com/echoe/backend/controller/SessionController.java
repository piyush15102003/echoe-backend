package com.echoe.backend.controller;

import com.echoe.backend.dto.message.SendTextRequest;
import com.echoe.backend.dto.message.SendTextResponse;
import com.echoe.backend.dto.session.*;
import com.echoe.backend.dto.voice.SendVoiceResponse;
import com.echoe.backend.service.MessageService;
import com.echoe.backend.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final MessageService messageService;

    public SessionController(SessionService sessionService,
                             MessageService messageService) {
        this.sessionService = sessionService;
        this.messageService = messageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSessionResponse createSession(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateSessionRequest request) {
        return sessionService.startSession(userId, request);
    }

    @PostMapping("/{sessionId}/messages/text")
    public SendTextResponse sendTextMessage(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody SendTextRequest request) {
        sessionService.getActiveSession(userId, sessionId);
        return messageService.processTextMessage(userId, sessionId, request);
    }

    @PostMapping(value = "/{sessionId}/messages/voice",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SendVoiceResponse sendVoiceMessage(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId,
            @RequestPart("audio") MultipartFile audio,
            @RequestParam(value = "language", defaultValue = "hi-IN") String language)
            throws IOException {
        sessionService.getActiveSession(userId, sessionId);
        return messageService.processVoiceMessage(userId, sessionId, audio.getBytes(), language);
    }

    @PostMapping("/{sessionId}/end")
    public EndSessionResponse endSession(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId) {
        return sessionService.endSession(userId, sessionId);
    }

    @PostMapping("/{sessionId}/pause")
    public PauseSessionResponse pauseSession(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId) {
        return sessionService.pauseSession(userId, sessionId);
    }

    @PostMapping("/{sessionId}/resume")
    public ResumeSessionResponse resumeSession(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId) {
        return sessionService.resumeSession(userId, sessionId);
    }

    @GetMapping("/active")
    public ResponseEntity<ActiveSessionResponse> getActiveSession(
            @AuthenticationPrincipal UUID userId) {
        ActiveSessionResponse active = sessionService.getActiveOrPausedSession(userId);
        if (active == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(active);
    }
}
