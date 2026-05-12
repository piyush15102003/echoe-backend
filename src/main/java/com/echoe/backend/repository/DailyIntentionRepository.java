package com.echoe.backend.repository;

import com.echoe.backend.entity.DailyIntentionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DailyIntentionRepository extends JpaRepository<DailyIntentionEntity, UUID> {
    Optional<DailyIntentionEntity> findByUserIdAndDate(UUID userId, LocalDate date);
    boolean existsByUserIdAndDate(UUID userId, LocalDate date);
}
