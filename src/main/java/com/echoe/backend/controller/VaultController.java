package com.echoe.backend.controller;

import com.echoe.backend.dto.vault.*;
import com.echoe.backend.service.VaultService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vault")
public class VaultController {

    private final VaultService vaultService;

    public VaultController(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    @GetMapping("/settings")
    public VaultSettingsResponse getSettings(@AuthenticationPrincipal UUID userId) {
        return vaultService.getSettings(userId);
    }

    @PostMapping("/settings")
    public VaultSettingsResponse updateSettings(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody VaultSettingsRequest request) {
        return vaultService.updateSettings(userId, request);
    }

    @GetMapping("/sessions")
    public List<VaultSessionSummary> listSessions(
            @AuthenticationPrincipal UUID userId) {
        return vaultService.listSessions(userId);
    }

    @GetMapping("/sessions/{sessionId}")
    public VaultSessionDetail getSessionDetail(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId) {
        return vaultService.getSessionDetail(userId, sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId) {
        vaultService.deleteSession(userId, sessionId);
    }
}
