package com.swiftdrop.delivery.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Parcels; used only inside the delivery module (ADR 0003). */
public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    /** Expects a number already normalized by {@link TrackingNumberGenerator#normalize(String)}. */
    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    boolean existsByTrackingNumber(String trackingNumber);

    Optional<Parcel> findByMerchantIdAndMerchantOrderReference(UUID merchantId, String merchantOrderReference);
}
