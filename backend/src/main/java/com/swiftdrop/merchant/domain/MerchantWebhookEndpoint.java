package com.swiftdrop.merchant.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * Merchant webhook endpoint subscription aggregate (ADR 0003, docs/merchant-contracts.md).
 * Stores endpoint URLs, subscribed parcel-status events, and AES-GCM encrypted signing secrets.
 */
@Entity
@Table(name = "merchant_webhook_endpoints")
public class MerchantWebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "secret_ciphertext", nullable = false, length = 512)
    private String secretCiphertext;

    @Column(name = "secret_iv", nullable = false, length = 64)
    private String secretIv;

    @Column(name = "secret_key_version", nullable = false)
    private int secretKeyVersion;

    @Column(name = "secret_hint", nullable = false, length = 10)
    private String secretHint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "merchant_webhook_endpoint_events", joinColumns = @JoinColumn(name = "endpoint_id"))
    @Column(name = "event_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Set<ParcelStatus> events = new HashSet<>();

    protected MerchantWebhookEndpoint() {
        // for JPA
    }

    public MerchantWebhookEndpoint(UUID merchantId, String url, String description,
            String secretCiphertext, String secretIv, int secretKeyVersion, String secretHint,
            Set<ParcelStatus> events) {
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId must not be null");
        this.url = Objects.requireNonNull(url, "url must not be null").trim();
        this.description = description != null && !description.isBlank() ? description.trim() : null;
        this.enabled = true;
        this.secretCiphertext = Objects.requireNonNull(secretCiphertext, "secretCiphertext must not be null");
        this.secretIv = Objects.requireNonNull(secretIv, "secretIv must not be null");
        this.secretKeyVersion = secretKeyVersion;
        this.secretHint = Objects.requireNonNull(secretHint, "secretHint must not be null");
        if (events != null && !events.isEmpty()) {
            this.events.addAll(events);
        }
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String url, String description, Set<ParcelStatus> events) {
        if (isDeleted()) {
            throw new IllegalStateException("Cannot update a deleted webhook endpoint");
        }
        this.url = Objects.requireNonNull(url, "url must not be null").trim();
        this.description = description != null && !description.isBlank() ? description.trim() : null;
        this.events.clear();
        if (events != null) {
            this.events.addAll(events);
        }
        this.updatedAt = Instant.now();
    }

    public void rotateSecret(String secretCiphertext, String secretIv, int secretKeyVersion, String secretHint) {
        if (isDeleted()) {
            throw new IllegalStateException("Cannot rotate secret of a deleted webhook endpoint");
        }
        this.secretCiphertext = Objects.requireNonNull(secretCiphertext, "secretCiphertext must not be null");
        this.secretIv = Objects.requireNonNull(secretIv, "secretIv must not be null");
        this.secretKeyVersion = secretKeyVersion;
        this.secretHint = Objects.requireNonNull(secretHint, "secretHint must not be null");
        this.updatedAt = Instant.now();
    }

    public void enable() {
        if (isDeleted()) {
            throw new IllegalStateException("Cannot enable a deleted webhook endpoint");
        }
        this.enabled = true;
        this.updatedAt = Instant.now();
    }

    public void disable() {
        if (isDeleted()) {
            throw new IllegalStateException("Cannot disable a deleted webhook endpoint");
        }
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        if (!isDeleted()) {
            Instant now = Instant.now();
            this.deletedAt = now;
            this.enabled = false;
            this.updatedAt = now;
        }
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getSecretCiphertext() {
        return secretCiphertext;
    }

    public String getSecretIv() {
        return secretIv;
    }

    public int getSecretKeyVersion() {
        return secretKeyVersion;
    }

    public String getSecretHint() {
        return secretHint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Set<ParcelStatus> getEvents() {
        return Collections.unmodifiableSet(events);
    }
}

