package com.echoe.backend.repository;

import com.echoe.backend.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {
    List<MessageEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
    List<MessageEntity> findTop10BySessionIdOrderByCreatedAtDesc(UUID sessionId);
}
