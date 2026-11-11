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
}
