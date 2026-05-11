package com.echoe.backend.service;

import com.echoe.backend.dto.auth.AnonymousAuthRequest;
import com.echoe.backend.dto.auth.AuthResponse;
import com.echoe.backend.dto.auth.RefreshRequest;
import com.echoe.backend.dto.auth.TokenResponse;
import com.echoe.backend.entity.RefreshTokenEntity;
import com.echoe.backend.entity.UserEntity;
import com.echoe.backend.exception.AuthException;
import com.echoe.backend.repository.RefreshTokenRepository;
import com.echoe.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
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
