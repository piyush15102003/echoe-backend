package com.echoe.backend.service;

import com.echoe.backend.dto.auth.*;
import com.echoe.backend.entity.RefreshTokenEntity;
import com.echoe.backend.entity.UserEntity;
import com.echoe.backend.exception.AuthException;
import com.echoe.backend.repository.RefreshTokenRepository;
import com.echoe.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MAX_PIN_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 5;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse createAnonymousUser(AnonymousAuthRequest request) {
        UserEntity user = userRepository.findByDeviceId(request.deviceId())
                .orElseGet(() -> {
                    UserEntity newUser = new UserEntity(
                            request.deviceId(),
                            request.preferredLanguage(),
                            request.voicePreference()
                    );
                    return userRepository.save(newUser);
                });

        log.info("Anonymous auth for device {}, user {}", request.deviceId(), user.getId());
        return issueTokenPair(user);
    }

    @Transactional
    public TokenResponse refreshTokens(RefreshRequest request) {
        String incomingToken = request.refreshToken();

        Claims claims;
        try {
            claims = jwtService.parseToken(incomingToken);
        } catch (JwtException e) {
            throw new AuthException("Invalid refresh token");
        }

        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new AuthException("Token is not a refresh token");
        }

        String tokenHash = hashToken(incomingToken);
        RefreshTokenEntity storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthException("Refresh token not found"));

        // Theft detection: if token already revoked, revoke entire family
        if (storedToken.isRevoked()) {
            refreshTokenRepository.revokeAllByFamilyId(storedToken.getFamilyId());
            log.warn("Refresh token reuse detected for user {}", storedToken.getUserId());
            throw new AuthException("Token reuse detected — all sessions invalidated");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthException("Refresh token expired");
        }

        // Revoke current token
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        // Issue new pair in the same family
        UUID userId = jwtService.extractUserId(claims);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        return issueTokenPairInFamily(user, storedToken.getFamilyId());
    }

    @Transactional
    public SuccessResponse setPin(UUID userId, PinRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        user.setPinHash(passwordEncoder.encode(request.pin()));
        user.setFailedPinAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        log.info("PIN set for user {}", userId);
        return new SuccessResponse(true);
    }

    @Transactional
    public PinVerifyResponse verifyPin(UUID userId, PinRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        if (user.getPinHash() == null) {
            return new PinVerifyResponse(false, null, null);
        }

        // Check lockout
        if (user.getLockedUntil() != null && Instant.now().isBefore(user.getLockedUntil())) {
            return new PinVerifyResponse(false, 0, user.getLockedUntil().toString());
        }

        // Clear expired lockout
        if (user.getLockedUntil() != null) {
            user.setLockedUntil(null);
            user.setFailedPinAttempts(0);
        }

        if (passwordEncoder.matches(request.pin(), user.getPinHash())) {
            user.setFailedPinAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
            return new PinVerifyResponse(true, null, null);
        }

        // Wrong PIN
        int attempts = user.getFailedPinAttempts() + 1;
        user.setFailedPinAttempts(attempts);

        if (attempts >= MAX_PIN_ATTEMPTS) {
            Instant lockedUntil = Instant.now().plusSeconds(LOCKOUT_MINUTES * 60);
            user.setLockedUntil(lockedUntil);
            userRepository.save(user);
            log.warn("PIN lockout triggered for user {}", userId);
            return new PinVerifyResponse(false, 0, lockedUntil.toString());
        }

        userRepository.save(user);
        return new PinVerifyResponse(false, MAX_PIN_ATTEMPTS - attempts, null);
    }

    @Transactional
    public SuccessResponse wipeAccount(UUID userId, PinRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        if (user.getPinHash() == null || !passwordEncoder.matches(request.pin(), user.getPinHash())) {
            throw new AuthException("Invalid PIN");
        }

        // Revoke all tokens
        refreshTokenRepository.revokeAllByUserId(userId);

        // Delete the user (cascade will handle sessions, messages, etc.)
        userRepository.delete(user);

        log.info("Account wiped for user {}", userId);
        return new SuccessResponse(true);
    }

    private AuthResponse issueTokenPair(UserEntity user) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getDeviceId(), user.getSubscriptionTier());
        String refreshToken = jwtService.generateRefreshToken(
                user.getId(), user.getDeviceId(), user.getSubscriptionTier());

        UUID familyId = UUID.randomUUID();
        refreshTokenRepository.save(new RefreshTokenEntity(
                user.getId(), hashToken(refreshToken), familyId,
                jwtService.getRefreshExpiration()));

        return new AuthResponse(
                user.getId(),
                accessToken,
                refreshToken,
                jwtService.getAccessExpiration(),
                jwtService.getRefreshExpiration()
        );
    }

    private TokenResponse issueTokenPairInFamily(UserEntity user, UUID familyId) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getDeviceId(), user.getSubscriptionTier());
        String refreshToken = jwtService.generateRefreshToken(
                user.getId(), user.getDeviceId(), user.getSubscriptionTier());

        refreshTokenRepository.save(new RefreshTokenEntity(
                user.getId(), hashToken(refreshToken), familyId,
                jwtService.getRefreshExpiration()));

        return new TokenResponse(
                accessToken,
                refreshToken,
                jwtService.getAccessExpiration(),
                jwtService.getRefreshExpiration()
        );
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
