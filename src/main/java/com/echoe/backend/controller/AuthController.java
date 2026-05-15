package com.echoe.backend.controller;

import com.echoe.backend.dto.auth.*;
import com.echoe.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/anonymous")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse createAnonymousUser(@Valid @RequestBody AnonymousAuthRequest request) {
        return authService.createAnonymousUser(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refreshTokens(@Valid @RequestBody RefreshRequest request) {
        return authService.refreshTokens(request);
    }

    @PostMapping("/set-pin")
    public SuccessResponse setPin(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody PinRequest request) {
        return authService.setPin(userId, request);
    }

    @PostMapping("/verify-pin")
    public PinVerifyResponse verifyPin(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody PinRequest request) {
        return authService.verifyPin(userId, request);
    }

    @DeleteMapping("/wipe")
    public SuccessResponse wipeAccount(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody PinRequest request) {
        return authService.wipeAccount(userId, request);
    }
}
