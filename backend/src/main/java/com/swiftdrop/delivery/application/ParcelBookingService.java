package com.swiftdrop.delivery.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.TrackingNumberGenerator;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;

/**
 * Creates parcels for customers (CUS-2) and merchants (MER-2) with the same rules: validated
 * addresses, a fee recalculated by pricing (never taken from the client), a fresh tracking number,
 * and the {@code CREATED} history entry and outbox event in the same transaction.
 */
@Service
public class ParcelBookingService {

    static final int TRACKING_NUMBER_ATTEMPTS = 5;

    private final ParcelRepository parcels;
    private final FeeCalculator feeCalculator;
    private final TrackingNumberGenerator trackingNumbers;
    private final ParcelEventRecorder recorder;
    private final Clock clock;

    @Autowired
    public ParcelBookingService(ParcelRepository parcels, FeeCalculator feeCalculator,
            TrackingNumberGenerator trackingNumbers, ParcelEventRecorder recorder) {
        this(parcels, feeCalculator, trackingNumbers, recorder, Clock.systemUTC());
    }

    ParcelBookingService(ParcelRepository parcels, FeeCalculator feeCalculator, TrackingNumberGenerator trackingNumbers,
            ParcelEventRecorder recorder, Clock clock) {
        this.parcels = parcels;
        this.feeCalculator = feeCalculator;
        this.trackingNumbers = trackingNumbers;
        this.recorder = recorder;
        this.clock = clock;
    }

    /**
     * Books a delivery for a customer, who becomes its owner.
     *
     * @throws IllegalArgumentException if the booking is invalid
     */
    @Transactional
    public Parcel bookForCustomer(UUID customerId, Booking booking) {
        Objects.requireNonNull(customerId, "customerId");
        Checked checked = check(booking);
        Instant now = clock.instant();
        Parcel parcel = Parcel.forCustomer(nextTrackingNumber(), customerId, checked.pickup(), checked.dropoff(),
                booking.size(), booking.weightGrams(), booking.description(), fee(checked, booking), now);
        return saveAndRecord(parcel, Actor.customer(customerId), now);
    }

    /**
     * Creates a parcel for a merchant order. Does not look for an existing order with the same
     * reference; {@link MerchantParcelCreation} does, and the unique constraint catches races.
     *
     * @throws IllegalArgumentException if the order is invalid
     * @throws org.springframework.dao.DataIntegrityViolationException if the order reference exists
     */
    @Transactional
    public Parcel bookForMerchant(NewMerchantParcel order) {
        Objects.requireNonNull(order, "order");
        Booking booking = new Booking(order.pickup(), order.dropoff(), order.size(), order.weightGrams(),
                order.description());
        Checked checked = check(booking);
        Instant now = clock.instant();
        Parcel parcel = Parcel.forMerchant(nextTrackingNumber(), order.merchantId(), order.merchantOrderReference(),
                checked.pickup(), checked.dropoff(), booking.size(), booking.weightGrams(), booking.description(),
                fee(checked, booking), now);
        return saveAndRecord(parcel, Actor.merchantApi(), now);
    }

    /** Builds the addresses first, so invalid input never reaches pricing. */
    private static Checked check(Booking booking) {
        Objects.requireNonNull(booking, "booking");
        if (booking.size() == null) {
            throw new IllegalArgumentException("size is required");
        }
        if (booking.weightGrams() < Parcel.MIN_WEIGHT_GRAMS || booking.weightGrams() > Parcel.MAX_WEIGHT_GRAMS) {
            throw new IllegalArgumentException("weightGrams must be between 1 and 30000");
        }
        return new Checked(address(booking.pickup(), "pickup"), address(booking.dropoff(), "dropoff"));
    }

    private static ParcelAddress address(DeliveryContact contact, String name) {
        if (contact == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return new ParcelAddress(contact.contactName(), contact.phone(), contact.line1(), contact.line2(),
                contact.city(), contact.zoneId(), contact.postalCode());
    }

    private Parcel.Fee fee(Checked checked, Booking booking) {
        FeeQuote quote = feeCalculator.calculateFee(checked.pickup().getZoneId(), checked.dropoff().getZoneId(),
                booking.size(), booking.weightGrams());
        return new Parcel.Fee(quote.totalFee(), quote.currency(), quote.pricingRuleId());
    }

    /**
     * A number not yet in use. Collisions are rare (50 random bits); the unique constraint still
     * guards the window between this check and the insert.
     */
    private String nextTrackingNumber() {
        for (int attempt = 0; attempt < TRACKING_NUMBER_ATTEMPTS; attempt++) {
            String candidate = trackingNumbers.next();
            if (!parcels.existsByTrackingNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a free tracking number");
    }

    private Parcel saveAndRecord(Parcel parcel, Actor actor, Instant now) {
        // Flush so constraint violations surface here, inside this transaction
        Parcel saved = parcels.saveAndFlush(parcel);
        recorder.record(saved, null, actor, null, null, now);
        return saved;
    }

    private record Checked(ParcelAddress pickup, ParcelAddress dropoff) {
    }
}
