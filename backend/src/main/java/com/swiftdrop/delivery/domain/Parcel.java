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

    // The route, fixed at pickup (ADR 0009 §1); all null for parcels that follow the ADR 0006 rules
    @Column(name = "origin_hub_id")
    private UUID originHubId;

    @Column(name = "via_hub_id")
    private UUID viaHubId;

    @Column(name = "destination_hub_id")
    private UUID destinationHubId;

    /** The hub the parcel is at; set only while {@code AT_HUB}. */
    @Column(name = "current_hub_id")
    private UUID currentHubId;

    /** The hub the parcel travels to; set only while {@code IN_TRANSIT} on its route. */
    @Column(name = "next_hub_id")
    private UUID nextHubId;

    /** Delivery attempts started (ADR 0009 §3). */
    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_failure_reason", length = 40)
    private FailureReason lastFailureReason;

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

    /** Assigns or reassigns the pickup courier before pickup. The caller must lock this parcel row. */
    public void assignCourier(UUID courierUserId, Instant now) {
        assignPickupCourier(courierUserId, now);
    }

    public void assignPickupCourier(UUID courierUserId, Instant now) {
        Objects.requireNonNull(courierUserId, "courierUserId");
        Objects.requireNonNull(now, "now");
        if (status != ParcelStatus.CREATED) {
            throw new ParcelConflictException("A courier can only be assigned before pickup");
        }
        setCurrentCourier(courierUserId, now);
    }

    /**
     * Assigns or reassigns the courier for the next delivery attempt (ADR 0009 §5): only at the
     * destination hub, and only while another attempt is allowed.
     */
    public void assignDeliveryCourier(UUID courierUserId, int maxAttempts, Instant now) {
        Objects.requireNonNull(courierUserId, "courierUserId");
        if (!isAtDestinationHub()) {
            throw new ParcelConflictException("A delivery courier can only be assigned at the destination hub");
        }
        if (!canAttemptDelivery(maxAttempts)) {
            throw new ParcelConflictException("No further delivery attempt is allowed; start the return");
        }
        setCurrentCourier(courierUserId, now);
    }

    /** Assigns or reassigns the courier who takes the parcel back to the sender (ADR 0009 §5). */
    public void assignReturnCourier(UUID courierUserId, Instant now) {
        Objects.requireNonNull(courierUserId, "courierUserId");
        if (status != ParcelStatus.RETURNING) {
            throw new ParcelConflictException("A return courier can only be assigned while the parcel is returning");
        }
        setCurrentCourier(courierUserId, now);
    }

    private void setCurrentCourier(UUID courierUserId, Instant now) {
        this.assignedCourierId = courierUserId;
        this.assignedAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    private void clearCurrentCourier() {
        this.assignedCourierId = null;
        this.assignedAt = null;
    }

    // ---- Courier steps --------------------------------------------------------------------------

    /**
     * Pickup by the assigned pickup courier. With a route the parcel enters the hub network and the
     * pickup task ends (ADR 0009 §3); without one (its zones were not served at pickup time) it
     * follows the ADR 0006 rules with the same courier to the end.
     *
     * @param route {@code null} only when the zones are not served by hubs
     */
    public void pickUp(UUID courierUserId, HubRoute route, Instant now) {
        requireAssignedCourier(courierUserId);
        move(ParcelStatus.PICKED_UP, now);
        if (route != null) {
            this.originHubId = route.origin();
            this.viaHubId = route.via();
            this.destinationHubId = route.destination();
            clearCurrentCourier();
        }
    }

    /** The assigned delivery courier leaves the destination hub with the parcel: the next attempt starts. */
    public void startDelivery(UUID courierUserId, int maxAttempts, Instant now) {
        requireAssignedCourier(courierUserId);
        if (!isAtDestinationHub()) {
            throw new ParcelConflictException("Delivery starts only from the destination hub");
        }
        if (!canAttemptDelivery(maxAttempts)) {
            throw new ParcelConflictException("No further delivery attempt is allowed; start the return");
        }
        move(ParcelStatus.OUT_FOR_DELIVERY, now);
        this.currentHubId = null;
        this.deliveryAttempts++;
    }

    /** Handover to the recipient with proof (ADR 0006 §5). */
    public void deliver(UUID courierUserId, boolean proofCaptured, Instant now) {
        requireAssignedCourier(courierUserId);
        if (!proofCaptured) {
            throw new ParcelConflictException("Proof of delivery is required");
        }
        move(ParcelStatus.DELIVERED, now);
        clearCurrentCourier();
    }

    /**
     * A failed attempt (ADR 0009 §3, §4): the parcel goes back to the destination hub. Damage is
     * not an attempt reason; a dispatcher marks such a parcel {@code FAILED}.
     */
    public void failAttempt(UUID courierUserId, FailureReason reason, Instant now) {
        requireAssignedCourier(courierUserId);
        requireHubRoute();
        if (reason == null) {
            throw new ParcelConflictException("A failure reason is required");
        }
        if (!reason.isAttemptReason()) {
            throw new ParcelConflictException("A damaged parcel is marked failed by a dispatcher");
        }
        move(ParcelStatus.DELIVERY_ATTEMPT_FAILED, now);
        this.lastFailureReason = reason;
        clearCurrentCourier();
    }

    /** Handover back to the sender with proof (ADR 0009 §3). */
    public void completeReturn(UUID courierUserId, boolean proofCaptured, Instant now) {
        requireAssignedCourier(courierUserId);
        if (!proofCaptured) {
            throw new ParcelConflictException("Proof of the return is required");
        }
        move(ParcelStatus.RETURNED, now);
        clearCurrentCourier();
    }

    /**
     * A courier step of the ADR 0006 flow: pickup without a route, then one step at a time with the
     * same courier, {@code FAILED} with a reason. Parcels in the hub network use the methods above.
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
        requireAssignedCourier(courierUserId);
        if (failureReason != null && target != ParcelStatus.FAILED) {
            throw new IllegalArgumentException("A failure reason is only allowed for FAILED");
        }
        if (status == ParcelStatus.CREATED && target == ParcelStatus.PICKED_UP) {
            pickUp(courierUserId, null, now);
            return;
        }
        if (!status.isFinal() && !isLegacy()) {
            throw new ParcelConflictException("Illegal status transition");
        }
        checkMove(target);
        if (target == ParcelStatus.FAILED && failureReason == null) {
            throw new ParcelConflictException("A failure reason is required");
        }
        if (target == ParcelStatus.DELIVERED && !proofCaptured) {
            throw new ParcelConflictException("Proof of delivery is required");
        }
        move(target, now);
        if (target == ParcelStatus.FAILED) {
            this.lastFailureReason = failureReason;
        }
    }

    // ---- Dispatcher steps -----------------------------------------------------------------------

    /**
     * Scans the parcel in at a hub: its origin hub after pickup, the next hub of its route after a
     * line-haul leg, or its destination hub after a failed attempt (ADR 0009 §3).
     */
    public void scanAtHub(UUID hubId, Instant now) {
        Objects.requireNonNull(hubId, "hubId");
        requireHubRoute();
        checkMove(ParcelStatus.AT_HUB);
        UUID expected = switch (status) {
            case PICKED_UP -> originHubId;
            case IN_TRANSIT -> nextHubId;
            default -> destinationHubId; // DELIVERY_ATTEMPT_FAILED, the only other way into AT_HUB
        };
        if (!hubId.equals(expected)) {
            throw new ParcelConflictException("The parcel is not routed to this hub");
        }
        move(ParcelStatus.AT_HUB, now);
        this.currentHubId = hubId;
        this.nextHubId = null;
    }

    /** Sends the parcel from its current hub to the next hub of its route (ADR 0009 §3). */
    public void sendToNextHub(Instant now) {
        requireHubRoute();
        checkMove(ParcelStatus.IN_TRANSIT);
        UUID next = getRoute().nextAfter(currentHubId);
        if (next == null) {
            throw new ParcelConflictException("The parcel is already at its destination hub");
        }
        move(ParcelStatus.IN_TRANSIT, now);
        this.nextHubId = next;
        this.currentHubId = null;
    }

    /**
     * Starts the return at the destination hub after a failed attempt, once the attempt limit is
     * reached or the last reason was not retryable (ADR 0009 §3, §4).
     */
    public void startReturn(int maxAttempts, Instant now) {
        requireHubRoute();
        if (!isAtDestinationHub() || lastFailureReason == null) {
            throw new ParcelConflictException("A return starts at the destination hub after a failed attempt");
        }
        if (canAttemptDelivery(maxAttempts)) {
            throw new ParcelConflictException("Another delivery attempt is still allowed");
        }
        move(ParcelStatus.RETURNING, now);
        this.currentHubId = null;
    }

    /**
     * Ends the parcel as {@code FAILED}: lost or damaged beyond use (ADR 0009 §3). Not before pickup.
     */
    public void markFailed(FailureReason reason, Instant now) {
        if (reason == null) {
            throw new ParcelConflictException("A failure reason is required");
        }
        if (status == ParcelStatus.CREATED) {
            throw new ParcelConflictException("A parcel that was not picked up is cancelled, not failed");
        }
        move(ParcelStatus.FAILED, now);
        this.lastFailureReason = reason;
        clearCurrentCourier();
    }

    /** Cancels before pickup (ADR 0009 §3). */
    public void cancel(Instant now) {
        move(ParcelStatus.CANCELLED, now);
        clearCurrentCourier();
    }

    // ---- Rules ----------------------------------------------------------------------------------

    /** Picked up without a route: follows the ADR 0006 rules. */
    public boolean isLegacy() {
        return status != ParcelStatus.CREATED && originHubId == null;
    }

    /** The route, or {@code null} before pickup and for legacy parcels. */
    public HubRoute getRoute() {
        return originHubId == null ? null : new HubRoute(originHubId, viaHubId, destinationHubId);
    }

    public boolean isAtDestinationHub() {
        return status == ParcelStatus.AT_HUB && currentHubId != null && currentHubId.equals(destinationHubId);
    }

    /** Below the attempt limit, and the last failure (if any) allows another try (ADR 0009 §4). */
    public boolean canAttemptDelivery(int maxAttempts) {
        return deliveryAttempts < maxAttempts && (lastFailureReason == null || lastFailureReason.allowsRetry());
    }

    private void requireAssignedCourier(UUID courierUserId) {
        if (assignedCourierId == null || !assignedCourierId.equals(courierUserId)) {
            throw new ParcelNotFoundException();
        }
    }

    private void requireHubRoute() {
        if (originHubId == null) {
            throw new ParcelConflictException("Illegal status transition");
        }
    }

    /** Rejects a move the lifecycle table does not allow from the current status. */
    private void checkMove(ParcelStatus target) {
        if (status.isFinal()) {
            throw new ParcelConflictException("Parcel is already in a final status");
        }
        if (!ParcelLifecycle.canMove(status, target, isLegacy())) {
            throw new ParcelConflictException("Illegal status transition");
        }
    }

    /** Checks the lifecycle table and takes the next sequence number (ADR 0004 §4.1). */
    private void move(ParcelStatus target, Instant now) {
        Objects.requireNonNull(now, "now");
        checkMove(target);
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

    public UUID getOriginHubId() {
        return originHubId;
    }

    public UUID getViaHubId() {
        return viaHubId;
    }

    public UUID getDestinationHubId() {
        return destinationHubId;
    }

    public UUID getCurrentHubId() {
        return currentHubId;
    }

    public UUID getNextHubId() {
        return nextHubId;
    }

    public int getDeliveryAttempts() {
        return deliveryAttempts;
    }

    public FailureReason getLastFailureReason() {
        return lastFailureReason;
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
