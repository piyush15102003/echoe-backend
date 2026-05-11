package com.echoe.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "audio_storage_path")
    private String audioStoragePath;

    @Column(name = "detected_emotion")
    private String detectedEmotion;

    @Column(name = "emotion_intensity")
    private Double emotionIntensity;

    @Column(name = "tone_used")
    private String toneUsed;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected MessageEntity() {}

    public MessageEntity(UUID sessionId, String role, String content) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }

    public UUID getSessionId() { return sessionId; }

    public String getRole() { return role; }

    public String getContent() { return content; }

    public String getAudioStoragePath() { return audioStoragePath; }
    public void setAudioStoragePath(String audioStoragePath) { this.audioStoragePath = audioStoragePath; }

    public String getDetectedEmotion() { return detectedEmotion; }
    public void setDetectedEmotion(String detectedEmotion) { this.detectedEmotion = detectedEmotion; }

    public Double getEmotionIntensity() { return emotionIntensity; }
    public void setEmotionIntensity(Double emotionIntensity) { this.emotionIntensity = emotionIntensity; }

    public String getToneUsed() { return toneUsed; }
    public void setToneUsed(String toneUsed) { this.toneUsed = toneUsed; }

    public Instant getCreatedAt() { return createdAt; }
}
