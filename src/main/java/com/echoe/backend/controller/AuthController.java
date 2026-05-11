package com.echoe.backend.controller;

import com.echoe.backend.dto.auth.AnonymousAuthRequest;
import com.echoe.backend.dto.auth.AuthResponse;
import com.echoe.backend.dto.auth.RefreshRequest;
import com.echoe.backend.dto.auth.TokenResponse;
import com.echoe.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
}
