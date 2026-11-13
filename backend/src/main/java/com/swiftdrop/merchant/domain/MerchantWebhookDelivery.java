package com.swiftdrop.merchant.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * One webhook to send: an event for one endpoint (ADR 0004 §7.1), unique per event and endpoint. The
 * payload is the event envelope as UTF-8 text; the sender signs and sends exactly these bytes without
 * re-serializing them.
 */
@Entity
@Table(name = "merchant_webhook_deliveries")
public class MerchantWebhookDelivery {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "endpoint_id", nullable = false, updatable = false)
    private UUID endpointId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(nullable = false, updatable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 30)
    private ParcelStatus eventType;

    @Column(nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WebhookDeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "manual_redeliveries", nullable = false)
    private int manualRedeliveries;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_outcome", length = 30)
    private WebhookAttemptOutcome lastOutcome;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    protected MerchantWebhookDelivery() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public int getSequence() {
        return sequence;
    }

    public ParcelStatus getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public WebhookDeliveryStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public int getManualRedeliveries() {
        return manualRedeliveries;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public WebhookAttemptOutcome getLastOutcome() {
        return lastOutcome;
    }

    public Integer getLastHttpStatus() {
        return lastHttpStatus;
    }

    public UUID getLeaseToken() {
        return leaseToken;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }
}
