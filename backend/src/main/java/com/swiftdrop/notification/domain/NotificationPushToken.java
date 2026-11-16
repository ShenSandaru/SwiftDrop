package com.swiftdrop.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persisted FCM push token associated with a user and platform.
 * Corresponds to table {@code notification_push_tokens}.
 */
@Entity
@Table(name = "notification_push_tokens")
public class NotificationPushToken {

    public static final String DEFAULT_PLATFORM = "WEB";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "TEXT", unique = true)
    private String token;

    @Column(nullable = false, length = 20)
    private String platform;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected NotificationPushToken() {
        // for JPA
    }

    public NotificationPushToken(UUID userId, String token, String platform, Instant now) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be null or blank");
        }
        this.token = token;
        this.platform = (platform == null || platform.isBlank()) ? DEFAULT_PLATFORM : platform;
        this.isActive = true;
        Instant timestamp = Objects.requireNonNull(now, "now must not be null");
        this.createdAt = timestamp;
        this.updatedAt = timestamp;
    }

    public void reassignToUser(UUID userId, String platform, Instant now) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        if (platform != null && !platform.isBlank()) {
            this.platform = platform;
        }
        this.isActive = true;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void deactivate(Instant now) {
        this.isActive = false;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markUsed(Instant now) {
        Instant timestamp = Objects.requireNonNull(now, "now must not be null");
        this.lastUsedAt = timestamp;
        this.updatedAt = timestamp;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public String getPlatform() {
        return platform;
    }

    public boolean isActive() {
        return isActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
