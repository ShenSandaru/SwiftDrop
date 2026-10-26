package com.swiftdrop.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Short-lived exchange code for Google OAuth token handoff (ADR 0005). Only the SHA-256 hash of
 * the raw code is stored.
 */
@Entity
@Table(name = "google_login_codes")
public class GoogleLoginCode {

    public static final int HASH_LENGTH = 64;

    @Id
    @Column(name = "code_hash", nullable = false, updatable = false, length = HASH_LENGTH)
    private String codeHash;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected GoogleLoginCode() {
        // for JPA
    }

    public GoogleLoginCode(String codeHash, UUID userId, Instant expiresAt) {
        this(codeHash, userId, Instant.now(), expiresAt, null);
    }

    public GoogleLoginCode(String codeHash, UUID userId, Instant createdAt, Instant expiresAt, Instant usedAt) {
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.usedAt = usedAt;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public void markUsed(Instant now) {
        this.usedAt = Objects.requireNonNull(now, "now");
    }
}
