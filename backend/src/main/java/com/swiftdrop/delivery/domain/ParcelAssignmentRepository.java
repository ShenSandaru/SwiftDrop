package com.swiftdrop.delivery.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftdrop.delivery.api.ParcelAssignmentStatus;
import com.swiftdrop.delivery.api.ParcelAssignmentType;

public interface ParcelAssignmentRepository extends JpaRepository<ParcelAssignmentRecord, UUID> {

    Optional<ParcelAssignmentRecord> findFirstByParcelIdAndStatus(
            UUID parcelId, ParcelAssignmentStatus status);

    List<ParcelAssignmentRecord> findByParcelIdOrderByAssignedAtAsc(UUID parcelId);

    Page<ParcelAssignmentRecord> findByTypeAndStatus(
            ParcelAssignmentType type, ParcelAssignmentStatus status, Pageable pageable);
}
