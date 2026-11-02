package com.swiftdrop.delivery.domain;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swiftdrop.delivery.api.ParcelStatus;

/** Parcels; used only inside the delivery module (ADR 0003). */
public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    /** Expects a number already normalized by {@link TrackingNumberGenerator#normalize(String)}. */
    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    boolean existsByTrackingNumber(String trackingNumber);

    Optional<Parcel> findByMerchantIdAndMerchantOrderReference(UUID merchantId, String merchantOrderReference);

    /** A customer's own parcels (CUS-5); the caller passes the sort. */
    Page<Parcel> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Parcel> findByCustomerIdAndStatus(UUID customerId, ParcelStatus status, Pageable pageable);

    /** All parcels in one status, for dispatchers/admins. */
    Page<Parcel> findByStatus(ParcelStatus status, Pageable pageable);

    /**
     * Loads a parcel and locks its row ({@code SELECT ... FOR UPDATE}) until the transaction ends, so
     * concurrent changes of one parcel run one after the other and get consecutive sequence numbers
     * (ADR 0004 §4.1). Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Parcel p WHERE p.id = :id")
    Optional<Parcel> findByIdForUpdate(@Param("id") UUID id);
}
