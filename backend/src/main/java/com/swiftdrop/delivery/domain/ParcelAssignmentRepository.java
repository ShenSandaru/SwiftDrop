package com.swiftdrop.delivery.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swiftdrop.delivery.api.ParcelAssignmentStatus;
import com.swiftdrop.delivery.api.ParcelAssignmentType;

public interface ParcelAssignmentRepository extends JpaRepository<ParcelAssignmentRecord, UUID> {

    Optional<ParcelAssignmentRecord> findFirstByParcelIdAndStatus(
            UUID parcelId, ParcelAssignmentStatus status);

    List<ParcelAssignmentRecord> findByParcelIdOrderByAssignedAtAsc(UUID parcelId);

    /**
     * A courier's tasks for the day (D-09): every active task, plus the tasks they completed since
     * {@code since}, oldest first.
     */
    @Query("""
            SELECT a FROM ParcelAssignmentRecord a
            WHERE a.courierId = :courierId
              AND (a.status = com.swiftdrop.delivery.api.ParcelAssignmentStatus.ACTIVE
                   OR (a.status = com.swiftdrop.delivery.api.ParcelAssignmentStatus.COMPLETED
                       AND a.completedAt >= :since))
            ORDER BY a.assignedAt ASC
            """)
    List<ParcelAssignmentRecord> findCourierDay(@Param("courierId") UUID courierId, @Param("since") Instant since);

    /** Whether the courier has or had any task on the parcel (ADR 0009 §5). */
    boolean existsByParcelIdAndCourierId(UUID parcelId, UUID courierId);

    Page<ParcelAssignmentRecord> findByTypeAndStatus(
            ParcelAssignmentType type, ParcelAssignmentStatus status, Pageable pageable);

    @Query("""
            SELECT a FROM ParcelAssignmentRecord a
            JOIN Parcel p ON p.id = a.parcelId
            WHERE a.type = :type
              AND a.status = :status
              AND ((p.status = com.swiftdrop.delivery.api.ParcelStatus.CREATED
                    AND ((p.originHubId IS NULL AND :hubZoneId IS NOT NULL AND p.pickup.zoneId = :hubZoneId)
                         OR p.originHubId = :hubId))
                   OR (p.status = com.swiftdrop.delivery.api.ParcelStatus.PICKED_UP
                       AND (p.originHubId = :hubId OR p.nextHubId = :hubId))
                   OR (p.status IN (com.swiftdrop.delivery.api.ParcelStatus.AT_HUB,
                                    com.swiftdrop.delivery.api.ParcelStatus.OUT_FOR_DELIVERY,
                                    com.swiftdrop.delivery.api.ParcelStatus.DELIVERY_ATTEMPT_FAILED)
                       AND p.currentHubId = :hubId)
                   OR (p.status IN (com.swiftdrop.delivery.api.ParcelStatus.IN_TRANSIT,
                                    com.swiftdrop.delivery.api.ParcelStatus.RETURNING)
                       AND (p.currentHubId = :hubId OR p.nextHubId = :hubId))
                   OR (p.status IN (com.swiftdrop.delivery.api.ParcelStatus.DELIVERED,
                                    com.swiftdrop.delivery.api.ParcelStatus.FAILED,
                                    com.swiftdrop.delivery.api.ParcelStatus.CANCELLED)
                       AND (p.currentHubId = :hubId
                            OR p.originHubId = :hubId
                            OR p.destinationHubId = :hubId
                            OR (p.originHubId IS NULL AND :hubZoneId IS NOT NULL AND p.pickup.zoneId = :hubZoneId))))
            """)
    Page<ParcelAssignmentRecord> findByTypeAndStatusAndHub(
            @Param("type") ParcelAssignmentType type,
            @Param("status") ParcelAssignmentStatus status,
            @Param("hubId") UUID hubId,
            @Param("hubZoneId") UUID hubZoneId,
            Pageable pageable);
}
