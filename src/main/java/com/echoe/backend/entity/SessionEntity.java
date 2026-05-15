package com.echoe.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class SessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "started_at", updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "input_mode")
    private String inputMode;

    @Column(name = "language")
    private String language;

    @Column(name = "summary_text")
    private String summaryText;

    @Column(name = "summary_quote")
    private String summaryQuote;

    @Column(name = "closing_reflection")
    private String closingReflection;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "emotion_tags", columnDefinition = "text[]")
    private String[] emotionTags;

    @Column(name = "crisis_flagged")
    private boolean crisisFlagged;

    @Column(name = "vault_expires_at")
    private Instant vaultExpiresAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected SessionEntity() {}

    public SessionEntity(UUID userId, String inputMode, String language) {
        this.userId = userId;
        this.inputMode = inputMode;
        this.language = language;
        this.crisisFlagged = false;
    }

    @PrePersist
    void prePersist() {
        this.startedAt = Instant.now();
    }

    public UUID getId() { return id; }

    public UUID getUserId() { return userId; }

    public Instant getStartedAt() { return startedAt; }

    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public Instant getPausedAt() { return pausedAt; }
    public void setPausedAt(Instant pausedAt) { this.pausedAt = pausedAt; }

    public String getInputMode() { return inputMode; }

    public String getLanguage() { return language; }

    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }

    public String getSummaryQuote() { return summaryQuote; }
    public void setSummaryQuote(String summaryQuote) { this.summaryQuote = summaryQuote; }

    public String getClosingReflection() { return closingReflection; }
    public void setClosingReflection(String closingReflection) { this.closingReflection = closingReflection; }

    public String[] getEmotionTags() { return emotionTags; }
    public void setEmotionTags(String[] emotionTags) { this.emotionTags = emotionTags; }

    public boolean isCrisisFlagged() { return crisisFlagged; }
    public void setCrisisFlagged(boolean crisisFlagged) { this.crisisFlagged = crisisFlagged; }

    public Instant getVaultExpiresAt() { return vaultExpiresAt; }
    public void setVaultExpiresAt(Instant vaultExpiresAt) { this.vaultExpiresAt = vaultExpiresAt; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
