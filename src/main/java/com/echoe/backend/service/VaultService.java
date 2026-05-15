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

import java.time.Instant;
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
        user.setVaultModeEnabled(request.enable());
        userRepository.save(user);
        return new VaultSettingsResponse(user.isVaultModeEnabled(), user.getPinHash() != null);
    }

    public List<VaultSessionSummary> listSessions(UUID userId) {
        UserEntity user = findUser(userId);
        verifyVaultEnabled(user);

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

    public VaultSessionDetail getSessionDetail(UUID userId, UUID sessionId) {
        UserEntity user = findUser(userId);
        verifyVaultEnabled(user);

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
    public void deleteSession(UUID userId, UUID sessionId) {
        UserEntity user = findUser(userId);
        verifyVaultEnabled(user);

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
}
