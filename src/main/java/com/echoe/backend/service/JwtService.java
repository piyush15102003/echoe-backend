package com.echoe.backend.service;

import com.echoe.backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(
                jwtProperties.secret().getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateAccessToken(UUID userId, UUID deviceId, String tier) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("device_id", deviceId.toString())
                .claim("tier", tier)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(jwtProperties.accessExpirationMs())))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId, UUID deviceId, String tier) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("device_id", deviceId.toString())
                .claim("tier", tier)
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(jwtProperties.refreshExpirationMs())))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return "access".equals(claims.get("type", String.class));
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public Instant getAccessExpiration() {
        return Instant.now().plusMillis(jwtProperties.accessExpirationMs());
    }

    public Instant getRefreshExpiration() {
        return Instant.now().plusMillis(jwtProperties.refreshExpirationMs());
    }
}
