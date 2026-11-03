package com.swiftdrop.delivery.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelLifecycle;
import com.swiftdrop.delivery.domain.ParcelRepository;

/**
 * A courier's deliveries for the day (COU-2, D-09): their unfinished parcels plus the ones they
 * finished today. "Today" starts at midnight in the business time zone ({@code Asia/Colombo} by
 * default), not in UTC.
 */
@Service
@Transactional(readOnly = true)
public class CourierParcelService {

    private static final EnumSet<ParcelStatus> TERMINAL = EnumSet.of(ParcelStatus.DELIVERED, ParcelStatus.FAILED);

    private final ParcelRepository parcels;
    private final DeliveryProofRepository proofs;
    private final ZoneId businessZone;
    private final Clock clock;

    @Autowired
    public CourierParcelService(ParcelRepository parcels, DeliveryProofRepository proofs,
            @Value("${swiftdrop.delivery.business-time-zone:Asia/Colombo}") String businessZone) {
        this(parcels, proofs, ZoneId.of(businessZone), Clock.systemUTC());
    }

    CourierParcelService(ParcelRepository parcels, DeliveryProofRepository proofs, ZoneId businessZone, Clock clock) {
        this.parcels = parcels;
        this.proofs = proofs;
        this.businessZone = businessZone;
        this.clock = clock;
    }

    /** One parcel on the courier's list. */
    public record CourierParcel(Parcel parcel, boolean proofCaptured, Set<ParcelStatus> nextStatuses) {

        public CourierParcel {
            Objects.requireNonNull(parcel, "parcel");
            nextStatuses = Set.copyOf(nextStatuses);
        }

        /** Never includes personal data. */
        @Override
        public String toString() {
            return "CourierParcel[parcel=" + parcel + ", proofCaptured=" + proofCaptured + "]";
        }
    }

    public List<CourierParcel> today(UUID courierId) {
        Objects.requireNonNull(courierId, "courierId");
        List<Parcel> day = parcels.findCourierDay(courierId, TERMINAL, startOfToday());
        if (day.isEmpty()) {
            return List.of();
        }
        Set<UUID> withProof = proofs.findParcelIdsWithProof(day.stream().map(Parcel::getId).toList());
        return day.stream()
                .map(parcel -> new CourierParcel(parcel, withProof.contains(parcel.getId()),
                        ParcelLifecycle.nextStatuses(parcel.getStatus())))
                .toList();
    }

    Instant startOfToday() {
        return LocalDate.now(clock.withZone(businessZone)).atStartOfDay(businessZone).toInstant();
    }
}
