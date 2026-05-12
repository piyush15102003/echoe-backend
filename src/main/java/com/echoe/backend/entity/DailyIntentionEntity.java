package com.echoe.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_intentions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "date"})
})
public class DailyIntentionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "intention_text", nullable = false)
    private String intentionText;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "generated_from_emotions", columnDefinition = "text[]")
    private String[] generatedFromEmotions;

    @Column(name = "viewed_at")
    private Instant viewedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected DailyIntentionEntity() {}

    public DailyIntentionEntity(UUID userId, LocalDate date, String intentionText, String[] generatedFromEmotions) {
        this.userId = userId;
        this.date = date;
        this.intentionText = intentionText;
        this.generatedFromEmotions = generatedFromEmotions;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }

    public UUID getUserId() { return userId; }

    public LocalDate getDate() { return date; }

    public String getIntentionText() { return intentionText; }

    public String[] getGeneratedFromEmotions() { return generatedFromEmotions; }

    public Instant getViewedAt() { return viewedAt; }
    public void setViewedAt(Instant viewedAt) { this.viewedAt = viewedAt; }

    public Instant getCreatedAt() { return createdAt; }
}
