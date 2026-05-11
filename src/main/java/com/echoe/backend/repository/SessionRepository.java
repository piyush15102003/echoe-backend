package com.echoe.backend.repository;

import com.echoe.backend.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {
    Optional<SessionEntity> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndEndedAtIsNull(UUID userId);
}
