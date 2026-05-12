package com.echoe.backend.controller;

import com.echoe.backend.dto.intention.IntentionResponse;
import com.echoe.backend.service.IntentionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intention")
public class IntentionController {

    private final IntentionService intentionService;

    public IntentionController(IntentionService intentionService) {
        this.intentionService = intentionService;
    }

    @GetMapping("/today")
    public ResponseEntity<IntentionResponse> getTodayIntention(
            @AuthenticationPrincipal UUID userId) {
        return intentionService.getTodayIntention(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/viewed")
    public ResponseEntity<Map<String, Boolean>> markViewed(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        intentionService.markViewed(id, userId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
