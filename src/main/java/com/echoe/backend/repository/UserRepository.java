package com.echoe.backend.repository;

import com.echoe.backend.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByDeviceId(UUID deviceId);

    // Metrics
    long countByCreatedAtAfter(Instant instant);
    long countByLastActiveAtAfter(Instant instant);
}
