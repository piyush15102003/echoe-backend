package com.echoe.backend.service;

import com.echoe.backend.dto.ai.SummaryResponse;
import com.echoe.backend.dto.chat.ChatMessage;
import com.echoe.backend.dto.session.*;
import com.echoe.backend.entity.MessageEntity;
import com.echoe.backend.entity.SessionEntity;
import com.echoe.backend.entity.UserEntity;
import com.echoe.backend.config.RateLimitProperties;
import com.echoe.backend.exception.RateLimitException;
import com.echoe.backend.exception.SessionException;
import com.echoe.backend.repository.MessageRepository;
import com.echoe.backend.repository.SessionRepository;
import com.echoe.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final String OPENING_MESSAGE = "Tell me what's on your mind.";
    private static final int VAULT_DAYS_FREE = 7;
    private static final int VAULT_DAYS_PREMIUM = 30;

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final AIService aiService;
    private final RateLimitProperties rateLimitProperties;

    public SessionService(SessionRepository sessionRepository,
                          UserRepository userRepository,
                          MessageRepository messageRepository,
                          AIService aiService,
                          RateLimitProperties rateLimitProperties) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.aiService = aiService;
        this.rateLimitProperties = rateLimitProperties;
    }

    @Transactional
    public CreateSessionResponse startSession(UUID userId, CreateSessionRequest request) {
        if (sessionRepository.existsByUserIdAndEndedAtIsNull(userId)) {
            throw new SessionException("User already has an active session");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new SessionException("User not found"));

        // Enforce free tier daily session limit (2/day, resets at midnight IST)
        if (!"premium".equals(user.getSubscriptionTier())) {
            Instant startOfTodayIST = LocalDate.now(ZoneId.of("Asia/Kolkata"))
                    .atStartOfDay(ZoneId.of("Asia/Kolkata"))
                    .toInstant();
            long sessionsToday = sessionRepository
                    .countByUserIdAndStartedAtAfter(userId, startOfTodayIST);
            if (sessionsToday >= rateLimitProperties.freeSessionsPerDay()) {
                throw new RateLimitException("Daily limit reached: "
                        + rateLimitProperties.freeSessionsPerDay()
                        + " sessions per day. Come back tomorrow.");
            }
        }

        user.setLastActiveAt(Instant.now());
        userRepository.save(user);

        SessionEntity session = new SessionEntity(userId, request.inputMode(), request.language());
        session = sessionRepository.save(session);

        return new CreateSessionResponse(
                session.getId(),
                session.getStartedAt(),
                OPENING_MESSAGE
        );
    }

    @Transactional
    public EndSessionResponse endSession(UUID userId, UUID sessionId) {
        SessionEntity session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new SessionException("Session not found"));

        if (session.getEndedAt() != null) {
            throw new SessionException("Session already ended");
        }

        session.setEndedAt(Instant.now());

        // Generate summary from full conversation
        List<MessageEntity> allMessages =
                messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        if (!allMessages.isEmpty()) {
            List<ChatMessage> conversation = allMessages.stream()
                    .map(m -> new ChatMessage(m.getRole(), m.getContent()))
                    .toList();

            try {
                SummaryResponse summary = aiService.generateSummary(conversation);
                session.setSummaryText(summary.summaryText());
                session.setSummaryQuote(summary.summaryQuote());
                session.setClosingReflection(summary.closingReflection());
                if (summary.emotionTags() != null) {
                    session.setEmotionTags(summary.emotionTags().toArray(String[]::new));
                }
            } catch (Exception ex) {
                log.error("Failed to generate session summary for {}", sessionId, ex);
                // Don't fail session end if summary generation fails
            }
        }

        // Set vault expiry based on user's vault mode
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user != null && user.isVaultModeEnabled()) {
            int days = "premium".equals(user.getSubscriptionTier())
                    ? VAULT_DAYS_PREMIUM : VAULT_DAYS_FREE;
            session.setVaultExpiresAt(Instant.now().plus(days, ChronoUnit.DAYS));
        }

        sessionRepository.save(session);

        return new EndSessionResponse(
                session.getId(),
                session.getEndedAt(),
                "completed",
                session.getSummaryText(),
                session.getSummaryQuote(),
                session.getClosingReflection(),
                session.getEmotionTags(),
                session.isCrisisFlagged()
        );
    }

    public SessionEntity getActiveSession(UUID userId, UUID sessionId) {
        SessionEntity session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new SessionException("Session not found"));

        if (session.getEndedAt() != null) {
            throw new SessionException("Session already ended");
        }

        // Un-pause if resuming a paused session via message send
        if (session.getPausedAt() != null) {
            session.setPausedAt(null);
            sessionRepository.save(session);
        }

        return session;
    }

    @Transactional
    public PauseSessionResponse pauseSession(UUID userId, UUID sessionId) {
        SessionEntity session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new SessionException("Session not found"));

        if (session.getEndedAt() != null) {
            throw new SessionException("Session already ended");
        }

        session.setPausedAt(Instant.now());
        sessionRepository.save(session);

        return new PauseSessionResponse(session.getId(), session.getPausedAt());
    }

    @Transactional
    public ResumeSessionResponse resumeSession(UUID userId, UUID sessionId) {
        SessionEntity session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new SessionException("Session not found"));

        if (session.getEndedAt() != null) {
            throw new SessionException("Session already ended");
        }

        session.setPausedAt(null);
        sessionRepository.save(session);

        List<MessageEntity> allMessages =
                messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        List<ResumeSessionResponse.MessageItem> messages = allMessages.stream()
                .map(m -> new ResumeSessionResponse.MessageItem(
                        m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();

        return new ResumeSessionResponse(session.getId(), session.getInputMode(), messages);
    }

    public ActiveSessionResponse getActiveOrPausedSession(UUID userId) {
        return sessionRepository.findByUserIdAndEndedAtIsNull(userId)
                .map(s -> new ActiveSessionResponse(
                        s.getId(), s.getInputMode(), s.getStartedAt(),
                        s.getPausedAt() != null))
                .orElse(null);
    }
}
