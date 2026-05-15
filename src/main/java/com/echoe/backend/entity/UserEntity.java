package com.echoe.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_id", unique = true, nullable = false)
    private UUID deviceId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "vault_mode_enabled")
    private boolean vaultModeEnabled;

    @Column(name = "pin_hash")
    private String pinHash;

    @Column(name = "preferred_language")
    private String preferredLanguage;

    @Column(name = "voice_preference")
    private String voicePreference;

    @Column(name = "subscription_tier")
    private String subscriptionTier;

    @Column(name = "failed_pin_attempts")
    private Integer failedPinAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "sessions_this_week")
    private int sessionsThisWeek;

    @Column(name = "week_reset_at")
    private Instant weekResetAt;

    protected UserEntity() {}

    public UserEntity(UUID deviceId, String preferredLanguage, String voicePreference) {
        this.deviceId = deviceId;
        this.preferredLanguage = preferredLanguage;
        this.voicePreference = voicePreference;
        this.subscriptionTier = "free";
        this.sessionsThisWeek = 0;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastActiveAt = now;
        this.weekResetAt = now;
    }

    public UUID getId() { return id; }

    public UUID getDeviceId() { return deviceId; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public boolean isVaultModeEnabled() { return vaultModeEnabled; }
    public void setVaultModeEnabled(boolean vaultModeEnabled) { this.vaultModeEnabled = vaultModeEnabled; }

    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }

    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

    public String getVoicePreference() { return voicePreference; }
    public void setVoicePreference(String voicePreference) { this.voicePreference = voicePreference; }

    public String getSubscriptionTier() { return subscriptionTier; }
    public void setSubscriptionTier(String subscriptionTier) { this.subscriptionTier = subscriptionTier; }

    public int getFailedPinAttempts() { return failedPinAttempts != null ? failedPinAttempts : 0; }
    public void setFailedPinAttempts(int failedPinAttempts) { this.failedPinAttempts = failedPinAttempts; }

    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }

    public int getSessionsThisWeek() { return sessionsThisWeek; }
    public void setSessionsThisWeek(int sessionsThisWeek) { this.sessionsThisWeek = sessionsThisWeek; }

    public Instant getWeekResetAt() { return weekResetAt; }
    public void setWeekResetAt(Instant weekResetAt) { this.weekResetAt = weekResetAt; }
}
