package com.swiftdrop.merchant.domain;

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

import com.swiftdrop.merchant.api.MerchantStatus;

/**
 * Append-only audit record tracking merchant approval lifecycle state transitions (docs/merchant-contracts.md).
 */
@Entity
@Table(name = "merchant_status_history")
public class MerchantStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private MerchantStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private MerchantStatus toStatus;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MerchantStatusHistory() {
        // for JPA
    }

    public MerchantStatusHistory(UUID merchantId, MerchantStatus fromStatus, MerchantStatus toStatus,
            UUID actorUserId, String reason) {
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId must not be null");
        this.toStatus = Objects.requireNonNull(toStatus, "toStatus must not be null");
        this.fromStatus = fromStatus;
        this.actorUserId = actorUserId;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public MerchantStatus getFromStatus() {
        return fromStatus;
    }

    public MerchantStatus getToStatus() {
        return toStatus;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

