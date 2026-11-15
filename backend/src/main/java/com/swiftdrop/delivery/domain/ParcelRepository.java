package com.swiftdrop.delivery.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
import com.swiftdrop.delivery.api.ParcelSource;

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

    /** Parcels waiting for a courier (ADM-1); uses the partial index {@code ix_parcels_unassigned}. */
    Page<Parcel> findByStatusAndAssignedCourierIdIsNull(ParcelStatus status, Pageable pageable);

    /**
     * The parcels a staff member's hubs may see (ADR 0012 §3): those with one of the hubs on their
     * route, and, before pickup, those whose pickup zone a hub serves. Needs {@code :everywhere},
     * {@code :hubIds} and {@code :zoneIds}; the collections must not be empty, use {@link #NOBODY}.
     */
    String IN_SCOPE = """
            (:everywhere = true
             OR p.originHubId IN :hubIds OR p.viaHubId IN :hubIds OR p.destinationHubId IN :hubIds
             OR p.currentHubId IN :hubIds OR p.nextHubId IN :hubIds
             OR (p.status IN (com.swiftdrop.delivery.api.ParcelStatus.CREATED,
                              com.swiftdrop.delivery.api.ParcelStatus.CANCELLED)
                 AND p.pickup.zoneId IN :zoneIds))
            """;

    /** Stands in for an empty collection, which {@code IN} does not accept everywhere. */
    UUID NOBODY_ID = new UUID(0, 0);
    Collection<UUID> NOBODY = java.util.List.of(NOBODY_ID);

    /** Unfinished parcels last changed or assigned before a time (stale parcels), in a staff scope. */
    @Query("""
            SELECT p FROM Parcel p
            WHERE p.status NOT IN :statuses AND p.updatedAt < :before
              AND """ + IN_SCOPE)
    Page<Parcel> findStale(@Param("statuses") Collection<ParcelStatus> statuses, @Param("before") Instant before,
            @Param("everywhere") boolean everywhere, @Param("hubIds") Collection<UUID> hubIds,
            @Param("zoneIds") Collection<UUID> zoneIds, Pageable pageable);

    @Query("""
            SELECT p FROM Parcel p
            WHERE (:status IS NULL OR p.status = :status)
              AND (:source IS NULL OR p.source = :source)
              AND (:assigned IS NULL
                   OR (:assigned = true AND p.assignedCourierId IS NOT NULL)
                   OR (:assigned = false AND p.assignedCourierId IS NULL))
              AND """ + IN_SCOPE)
    Page<Parcel> findForDispatch(@Param("status") ParcelStatus status,
            @Param("source") ParcelSource source,
            @Param("assigned") Boolean assigned,
            @Param("everywhere") boolean everywhere,
            @Param("hubIds") Collection<UUID> hubIds,
            @Param("zoneIds") Collection<UUID> zoneIds,
            Pageable pageable);

    @Query("""
            SELECT p FROM Parcel p
            WHERE (:status IS NULL OR p.status = :status)
              AND (:source IS NULL OR p.source = :source)
              AND (:assigned IS NULL
                   OR (:assigned = true AND p.assignedCourierId IS NOT NULL)
                   OR (:assigned = false AND p.assignedCourierId IS NULL))
              AND (LOWER(p.trackingNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR (:parcelId IS NOT NULL AND p.id = :parcelId))
              AND """ + IN_SCOPE)
    Page<Parcel> findForDispatchBySearch(@Param("status") ParcelStatus status,
            @Param("source") ParcelSource source,
            @Param("assigned") Boolean assigned,
            @Param("search") String search,
            @Param("parcelId") UUID parcelId,
            @Param("everywhere") boolean everywhere,
            @Param("hubIds") Collection<UUID> hubIds,
            @Param("zoneIds") Collection<UUID> zoneIds,
            Pageable pageable);

    /** A merchant's parcels (MER-4); always scoped by merchant, never across merchants. */
    Page<Parcel> findByMerchantId(UUID merchantId, Pageable pageable);

    Page<Parcel> findByMerchantIdAndStatus(UUID merchantId, ParcelStatus status, Pageable pageable);

    /** Non-terminal parcels per assigned courier (ADM-2), as rows of {@code [courierId, count]}. */
    @Query("""
            SELECT p.assignedCourierId, COUNT(p) FROM Parcel p
            WHERE p.assignedCourierId IS NOT NULL AND p.status NOT IN :terminal
            GROUP BY p.assignedCourierId
            """)
    List<Object[]> countActiveByCourier(@Param("terminal") Collection<ParcelStatus> terminal);

    /**
     * Loads a parcel and locks its row ({@code SELECT ... FOR UPDATE}) until the transaction ends, so
     * concurrent changes of one parcel run one after the other and get consecutive sequence numbers
     * (ADR 0004 §4.1). Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Parcel p WHERE p.id = :id")
    Optional<Parcel> findByIdForUpdate(@Param("id") UUID id);
}
