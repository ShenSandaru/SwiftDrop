package com.swiftdrop.merchant.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Audit record of a manual webhook redelivery (ADR 0004 §7.5): who started which series, in which role, when,
 * and from which status. Never updated or deleted.
 */
@Entity
@Table(name = "merchant_webhook_redeliveries")
public class MerchantWebhookRedelivery {

    /** The role in which a redelivery was requested. */
    public enum RequestedBy {
        MERCHANT, ADMIN
    }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(nullable = false, updatable = false)
    private int series;

    @Column(name = "requested_by_user_id", nullable = false, updatable = false)
    private UUID requestedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_by_role", nullable = false, updatable = false, length = 20)
    private RequestedBy requestedByRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, updatable = false, length = 30)
    private WebhookDeliveryStatus previousStatus;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    protected MerchantWebhookRedelivery() {
        // for JPA
    }

    public MerchantWebhookRedelivery(UUID deliveryId, int series, UUID requestedByUserId, RequestedBy requestedByRole,
            WebhookDeliveryStatus previousStatus, Instant requestedAt) {
        this.id = UUID.randomUUID();
        this.deliveryId = deliveryId;
        this.series = series;
        this.requestedByUserId = requestedByUserId;
        this.requestedByRole = requestedByRole;
        this.previousStatus = previousStatus;
        this.requestedAt = requestedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeliveryId() {
        return deliveryId;
    }

    public int getSeries() {
        return series;
    }

    public UUID getRequestedByUserId() {
        return requestedByUserId;
    }

    public RequestedBy getRequestedByRole() {
        return requestedByRole;
    }

    public WebhookDeliveryStatus getPreviousStatus() {
        return previousStatus;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }
}
