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
 * One attempt to send a webhook delivery record (ADR 0004 §7.1): number, time, outcome category, HTTP status
 * and duration. No body, headers, signature or secret is kept.
 */
@Entity
@Table(name = "merchant_webhook_attempts")
public class MerchantWebhookAttempt {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private WebhookAttemptOutcome outcome;

    @Column(name = "http_status", updatable = false)
    private Integer httpStatus;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private int durationMs;

    @Column(name = "triggered_by_user_id", updatable = false)
    private UUID triggeredByUserId;

    /** 0 for the automatic series, 1 to 3 for the manual redelivery that started this attempt's series. */
    @Column(nullable = false, updatable = false)
    private int series;

    protected MerchantWebhookAttempt() {
        // for JPA
    }

    public MerchantWebhookAttempt(UUID deliveryId, int attemptNumber, Instant attemptedAt, WebhookAttemptOutcome outcome,
            Integer httpStatus, int durationMs, UUID triggeredByUserId) {
        this(deliveryId, attemptNumber, 0, attemptedAt, outcome, httpStatus, durationMs, triggeredByUserId);
    }

    public MerchantWebhookAttempt(UUID deliveryId, int attemptNumber, int series, Instant attemptedAt,
            WebhookAttemptOutcome outcome, Integer httpStatus, int durationMs, UUID triggeredByUserId) {
        this.id = UUID.randomUUID();
        this.deliveryId = deliveryId;
        this.attemptNumber = attemptNumber;
        this.series = series;
        this.attemptedAt = attemptedAt;
        this.outcome = outcome;
        this.httpStatus = httpStatus;
        this.durationMs = durationMs;
        this.triggeredByUserId = triggeredByUserId;
    }

    public int getSeries() {
        return series;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeliveryId() {
        return deliveryId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public WebhookAttemptOutcome getOutcome() {
        return outcome;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public UUID getTriggeredByUserId() {
        return triggeredByUserId;
    }
}
