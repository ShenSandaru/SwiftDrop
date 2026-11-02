package com.swiftdrop.delivery.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * The parcel aggregate (ADR 0003). Created in {@link ParcelStatus#CREATED} with sequence 1 and a fee
 * snapshot; owned by exactly one customer or one merchant, matching its {@link ParcelSource}.
 * Hibernate generates the UUID when the parcel is first persisted.
 */
@Entity
@Table(name = "parcels")
public class Parcel {

    public static final int MIN_WEIGHT_GRAMS = 1;
    public static final int MAX_WEIGHT_GRAMS = 30_000;
    public static final int MAX_DESCRIPTION_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 16)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParcelSource source;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "merchant_order_reference", length = 100)
    private String merchantOrderReference;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "contactName", column = @Column(name = "pickup_contact_name", nullable = false, length = 200)),
        @AttributeOverride(name = "phone", column = @Column(name = "pickup_phone", nullable = false, length = 20)),
        @AttributeOverride(name = "line1", column = @Column(name = "pickup_line1", nullable = false, length = 200)),
        @AttributeOverride(name = "line2", column = @Column(name = "pickup_line2", length = 200)),
        @AttributeOverride(name = "city", column = @Column(name = "pickup_city", nullable = false, length = 100)),
        @AttributeOverride(name = "zoneId", column = @Column(name = "pickup_zone_id", nullable = false)),
        @AttributeOverride(name = "postalCode", column = @Column(name = "pickup_postal_code", length = 10))
    })
    private ParcelAddress pickup;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "contactName", column = @Column(name = "dropoff_contact_name", nullable = false, length = 200)),
        @AttributeOverride(name = "phone", column = @Column(name = "dropoff_phone", nullable = false, length = 20)),
        @AttributeOverride(name = "line1", column = @Column(name = "dropoff_line1", nullable = false, length = 200)),
        @AttributeOverride(name = "line2", column = @Column(name = "dropoff_line2", length = 200)),
        @AttributeOverride(name = "city", column = @Column(name = "dropoff_city", nullable = false, length = 100)),
        @AttributeOverride(name = "zoneId", column = @Column(name = "dropoff_zone_id", nullable = false)),
        @AttributeOverride(name = "postalCode", column = @Column(name = "dropoff_postal_code", length = 10))
    })
    private ParcelAddress dropoff;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ParcelSize size;

    @Column(name = "weight_grams", nullable = false)
    private int weightGrams;

    @Column(length = 200)
    private String description;

    @Column(name = "fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeAmount;

    // The migration declares CHAR(3); without this Hibernate's validate expects VARCHAR(3)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "fee_currency", nullable = false, length = 3)
    private String feeCurrency;

    @Column(name = "pricing_rule_id", nullable = false)
    private UUID pricingRuleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ParcelStatus status;

    @Column(name = "last_sequence", nullable = false)
    private int lastSequence;

    @Column(name = "assigned_courier_id")
    private UUID assignedCourierId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    /** {@code null} for parcels created through the merchant API. */
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Parcel() {
        // for JPA
    }

    private Parcel(String trackingNumber, ParcelSource source, UUID customerId, UUID merchantId,
            String merchantOrderReference, ParcelAddress pickup, ParcelAddress dropoff, ParcelSize size,
            int weightGrams, String description, Fee fee, UUID createdBy, Instant now) {
        this.trackingNumber = Objects.requireNonNull(trackingNumber, "trackingNumber");
        this.source = source;
        this.customerId = customerId;
        this.merchantId = merchantId;
        this.merchantOrderReference = merchantOrderReference;
        this.pickup = Objects.requireNonNull(pickup, "pickup");
        this.dropoff = Objects.requireNonNull(dropoff, "dropoff");
        this.size = Objects.requireNonNull(size, "size");
        if (weightGrams < MIN_WEIGHT_GRAMS || weightGrams > MAX_WEIGHT_GRAMS) {
            throw new IllegalArgumentException("weightGrams must be between 1 and 30000");
        }
        this.weightGrams = weightGrams;
        this.description = description == null || description.isBlank() ? null : description.trim();
        if (this.description != null && this.description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("description must be at most 200 characters");
        }
        Objects.requireNonNull(fee, "fee");
        this.feeAmount = fee.amount();
        this.feeCurrency = fee.currency();
        this.pricingRuleId = fee.pricingRuleId();
        this.status = ParcelStatus.CREATED;
        this.lastSequence = 1;
        this.createdBy = createdBy;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    /** A delivery booked by a customer, who is both owner and creator. */
    public static Parcel forCustomer(String trackingNumber, UUID customerId, ParcelAddress pickup,
            ParcelAddress dropoff, ParcelSize size, int weightGrams, String description, Fee fee, Instant now) {
        Objects.requireNonNull(customerId, "customerId");
        return new Parcel(trackingNumber, ParcelSource.CUSTOMER, customerId, null, null, pickup, dropoff, size,
                weightGrams, description, fee, customerId, now);
    }

    /** A delivery ordered through the merchant API; there is no creating user. */
    public static Parcel forMerchant(String trackingNumber, UUID merchantId, String merchantOrderReference,
            ParcelAddress pickup, ParcelAddress dropoff, ParcelSize size, int weightGrams, String description,
            Fee fee, Instant now) {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(merchantOrderReference, "merchantOrderReference");
        if (merchantOrderReference.isBlank()) {
            throw new IllegalArgumentException("merchantOrderReference must not be blank");
        }
        return new Parcel(trackingNumber, ParcelSource.MERCHANT, null, merchantId, merchantOrderReference.trim(),
                pickup, dropoff, size, weightGrams, description, fee, null, now);
    }

    /**
     * Moves the parcel to {@code target} on behalf of a courier and takes the next sequence number
     * (ADR 0006 §1, ADR 0004 §4.1). The caller must hold a lock on the parcel row.
     *
     * <p>A courier who is not the assigned one gets {@link ParcelNotFoundException}, also when no
     * courier is assigned: such a caller is not authorized, and a conflict would reveal that the
     * parcel exists (ADR 0006 §7).
     *
     * @param failureReason required for {@code FAILED}, must be {@code null} otherwise
     * @param proofCaptured whether proof of delivery exists; required for {@code DELIVERED}
     * @throws ParcelNotFoundException if the courier is not the assigned courier
     * @throws ParcelConflictException if the move breaks a lifecycle rule
     * @throws IllegalArgumentException if a failure reason is given for another status
     */
    public void changeStatus(ParcelStatus target, UUID courierUserId, FailureReason failureReason,
            boolean proofCaptured, Instant now) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(now, "now");
        if (assignedCourierId == null || !assignedCourierId.equals(courierUserId)) {
            throw new ParcelNotFoundException();
        }
        if (failureReason != null && target != ParcelStatus.FAILED) {
            throw new IllegalArgumentException("A failure reason is only allowed for FAILED");
        }
        if (ParcelLifecycle.isTerminal(status)) {
            throw new ParcelConflictException("Parcel is already in a final status");
        }
        if (!ParcelLifecycle.canMove(status, target)) {
            throw new ParcelConflictException("Illegal status transition");
        }
        if (target == ParcelStatus.FAILED && failureReason == null) {
            throw new ParcelConflictException("A failure reason is required");
        }
        if (target == ParcelStatus.DELIVERED && !proofCaptured) {
            throw new ParcelConflictException("Proof of delivery is required");
        }
        this.status = target;
        this.lastSequence++;
        this.updatedAt = now;
    }

    /**
     * Fee snapshot from pricing, fixed at creation (contract §8.2).
     *
     * @param amount non-negative, 2 decimals
     */
    public record Fee(BigDecimal amount, String currency, UUID pricingRuleId) {

        public Fee {
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(pricingRuleId, "pricingRuleId");
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("amount must not be negative");
            }
            if (amount.scale() > 2) {
                throw new IllegalArgumentException("amount must have at most 2 decimals");
            }
            if (currency.length() != 3) {
                throw new IllegalArgumentException("currency must be a 3-letter code");
            }
        }
    }

    public UUID getId() {
        return id;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public ParcelSource getSource() {
        return source;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getMerchantOrderReference() {
        return merchantOrderReference;
    }

    public ParcelAddress getPickup() {
        return pickup;
    }

    public ParcelAddress getDropoff() {
        return dropoff;
    }

    public ParcelSize getSize() {
        return size;
    }

    public int getWeightGrams() {
        return weightGrams;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public String getFeeCurrency() {
        return feeCurrency;
    }

    public UUID getPricingRuleId() {
        return pricingRuleId;
    }

    public ParcelStatus getStatus() {
        return status;
    }

    public int getLastSequence() {
        return lastSequence;
    }

    public UUID getAssignedCourierId() {
        return assignedCourierId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    /** Never includes personal data (addresses, contacts, description). */
    @Override
    public String toString() {
        return "Parcel[id=" + id + ", trackingNumber=" + trackingNumber + ", source=" + source
                + ", status=" + status + ", lastSequence=" + lastSequence + "]";
    }
}
