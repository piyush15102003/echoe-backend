package com.echoe.backend.service;

import com.echoe.backend.dto.vault.*;
import com.echoe.backend.entity.MessageEntity;
import com.echoe.backend.entity.SessionEntity;
import com.echoe.backend.entity.UserEntity;
import com.echoe.backend.exception.AuthException;
import com.echoe.backend.exception.SessionException;
import com.echoe.backend.repository.MessageRepository;
import com.echoe.backend.repository.SessionRepository;
import com.echoe.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class VaultService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;

    public VaultService(UserRepository userRepository,
                        SessionRepository sessionRepository,
                        MessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    public VaultSettingsResponse getSettings(UUID userId) {
        UserEntity user = findUser(userId);
        return new VaultSettingsResponse(
                user.isVaultModeEnabled(),
                user.getPinHash() != null
        );
    }

    @Transactional
    public VaultSettingsResponse updateSettings(UUID userId, VaultSettingsRequest request) {
        UserEntity user = findUser(userId);

        if (request.enable()) {
            if (request.confirmPin() == null || !request.pin().equals(request.confirmPin())) {
                throw new AuthException("PIN and confirmation do not match");
            }
            user.setPinHash(hashPin(request.pin()));
            user.setVaultModeEnabled(true);
        } else {
            // Verify current PIN before disabling
            verifyPin(user, request.pin());
            user.setVaultModeEnabled(false);
        }

        userRepository.save(user);
        return new VaultSettingsResponse(user.isVaultModeEnabled(), user.getPinHash() != null);
    }

    public List<VaultSessionSummary> listSessions(UUID userId, String pin) {
        UserEntity user = findUser(userId);
        verifyVaultEnabled(user);
        verifyPin(user, pin);

        return sessionRepository
                .findByUserIdAndEndedAtIsNotNullAndDeletedAtIsNullOrderByEndedAtDesc(userId)
                .stream()
                .map(s -> new VaultSessionSummary(
                        s.getId(),
                        s.getStartedAt(),
                        s.getEndedAt(),
                        s.getSummaryText(),
                        s.getEmotionTags(),
                        s.isCrisisFlagged(),
                        s.getInputMode()
                ))
                .toList();
    }

    public VaultSessionDetail getSessionDetail(UUID userId, UUID sessionId, String pin) {
        UserEntity user = findUser(userId);
        verifyVaultEnabled(user);
        verifyPin(user, pin);

        SessionEntity session = sessionRepository.findByIdAndUserIdAndDeletedAtIsNull(sessionId, userId)
                .orElseThrow(() -> new SessionException("Session not found"));

        List<MessageEntity> messages =
                messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        List<VaultSessionDetail.MessageItem> messageItems = messages.stream()
                .map(m -> new VaultSessionDetail.MessageItem(
                        m.getId(),
                        m.getRole(),
                        m.getContent(),
                        m.getDetectedEmotion(),
                        m.getEmotionIntensity(),
                        m.getToneUsed(),
                        m.getCreatedAt()
                ))
                .toList();

        return new VaultSessionDetail(
                session.getId(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getSummaryText(),
                session.getSummaryQuote(),
                session.getClosingReflection(),
                session.getEmotionTags(),
                session.isCrisisFlagged(),
                session.getInputMode(),
                messageItems
        );
    }

    @Transactional
    public void deleteSession(UUID userId, UUID sessionId, String pin) {
        UserEntity user = findUser(userId);
        verifyVaultEnabled(user);
        verifyPin(user, pin);

        SessionEntity session = sessionRepository.findByIdAndUserIdAndDeletedAtIsNull(sessionId, userId)
                .orElseThrow(() -> new SessionException("Session not found"));

        session.setDeletedAt(Instant.now());
        sessionRepository.save(session);
    }

    private UserEntity findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));
    }

    private void verifyVaultEnabled(UserEntity user) {
        if (!user.isVaultModeEnabled()) {
            throw new AuthException("Vault mode is not enabled");
        }
    }

    private void verifyPin(UserEntity user, String pin) {
        if (user.getPinHash() == null) {
            throw new AuthException("No PIN set");
        }
        if (!user.getPinHash().equals(hashPin(pin))) {
            throw new AuthException("Invalid PIN");
        }
    }

    private String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pin.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
