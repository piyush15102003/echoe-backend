package com.echoe.backend.repository;

import com.echoe.backend.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {
    Optional<SessionEntity> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndEndedAtIsNull(UUID userId);
    Optional<SessionEntity> findByUserIdAndEndedAtIsNull(UUID userId);

    List<SessionEntity> findByUserIdAndEndedAtIsNotNullAndDeletedAtIsNullOrderByEndedAtDesc(UUID userId);
    Optional<SessionEntity> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    List<SessionEntity> findByVaultExpiresAtBeforeAndDeletedAtIsNull(Instant now);
}
