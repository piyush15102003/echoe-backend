package com.echoe.backend.service;

import com.echoe.backend.dto.session.CreateSessionRequest;
import com.echoe.backend.dto.session.CreateSessionResponse;
import com.echoe.backend.dto.session.EndSessionResponse;
import com.echoe.backend.entity.SessionEntity;
import com.echoe.backend.entity.UserEntity;
import com.echoe.backend.exception.SessionException;
import com.echoe.backend.repository.SessionRepository;
import com.echoe.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class SessionService {

    private static final String OPENING_MESSAGE = "Tell me what's on your mind.";

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    public SessionService(SessionRepository sessionRepository,
                          UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CreateSessionResponse startSession(UUID userId, CreateSessionRequest request) {
        if (sessionRepository.existsByUserIdAndEndedAtIsNull(userId)) {
            throw new SessionException("User already has an active session");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new SessionException("User not found"));
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
        sessionRepository.save(session);

        return new EndSessionResponse(
                session.getId(),
                session.getEndedAt(),
                "completed"
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
