package com.echoe.backend.service;

import com.echoe.backend.dto.ai.SummaryResponse;
import com.echoe.backend.dto.chat.ChatMessage;
import com.echoe.backend.dto.session.CreateSessionRequest;
import com.echoe.backend.dto.session.CreateSessionResponse;
import com.echoe.backend.dto.session.EndSessionResponse;
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

        // Reset weekly counter if week has elapsed
        if (user.getWeekResetAt() != null
                && Instant.now().isAfter(user.getWeekResetAt().plus(7, ChronoUnit.DAYS))) {
            user.setSessionsThisWeek(0);
            user.setWeekResetAt(Instant.now());
        }

        // Enforce free tier session limit
        if (!"premium".equals(user.getSubscriptionTier())
                && user.getSessionsThisWeek() >= rateLimitProperties.freeSessionsPerWeek()) {
            throw new RateLimitException("Free tier limit reached: "
                    + rateLimitProperties.freeSessionsPerWeek() + " sessions per week");
        }

        user.setLastActiveAt(Instant.now());
        user.setSessionsThisWeek(user.getSessionsThisWeek() + 1);
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

        return session;
    }
}
