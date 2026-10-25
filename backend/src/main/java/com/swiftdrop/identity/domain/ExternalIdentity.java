package com.swiftdrop.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Links a user to an identity at an external provider, keyed by the provider's stable subject
 * identifier. Immutable once created.
 */
@Entity
@Table(name = "external_identities")
public class ExternalIdentity {

    /** OIDC limits {@code sub} to 255 ASCII characters. */
    public static final int MAX_SUBJECT_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private IdentityProvider provider;

    @Column(nullable = false, updatable = false, length = MAX_SUBJECT_LENGTH)
    private String subject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExternalIdentity() {
        // for JPA
    }

    public ExternalIdentity(IdentityProvider provider, String subject, UUID userId) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public IdentityProvider getProvider() {
        return provider;
    }

    public String getSubject() {
        return subject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
