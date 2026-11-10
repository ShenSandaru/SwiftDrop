package com.swiftdrop.delivery.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.swiftdrop.delivery.api.ParcelAssignmentStatus;
import com.swiftdrop.delivery.api.ParcelAssignmentSummary;
import com.swiftdrop.delivery.api.ParcelAssignmentType;

@Entity
@Table(name = "parcel_assignments")
public class ParcelAssignmentRecord {

    @Id
    private UUID id;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false, length = 10)
    private ParcelAssignmentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ParcelAssignmentStatus status;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "dispatcher_id")
    private UUID dispatcherId;

    @Version
    @Column(nullable = false)
    private long version;

    protected ParcelAssignmentRecord() {
    }

    public ParcelAssignmentRecord(UUID parcelId, UUID courierId, ParcelAssignmentType type, UUID dispatcherId,
            Instant assignedAt) {
        this.id = UUID.randomUUID();
        this.parcelId = Objects.requireNonNull(parcelId, "parcelId");
        this.courierId = Objects.requireNonNull(courierId, "courierId");
        this.type = Objects.requireNonNull(type, "type");
        this.dispatcherId = Objects.requireNonNull(dispatcherId, "dispatcherId");
        this.status = ParcelAssignmentStatus.ACTIVE;
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt");
    }

    public void complete(Instant now) {
        changeStatus(ParcelAssignmentStatus.COMPLETED, now);
    }

    public void reassign(Instant now) {
        changeStatus(ParcelAssignmentStatus.REASSIGNED, now);
    }

    /** The parcel was cancelled or failed before the task was done (ADR 0009 §5). */
    public void cancel(Instant now) {
        changeStatus(ParcelAssignmentStatus.CANCELLED, now);
    }

    private void changeStatus(ParcelAssignmentStatus nextStatus, Instant now) {
        if (status != ParcelAssignmentStatus.ACTIVE) {
            throw new IllegalStateException("Only an active assignment can be completed, reassigned or cancelled");
        }
        status = nextStatus;
        completedAt = Objects.requireNonNull(now, "now");
    }

    public ParcelAssignmentSummary toSummary() {
        return new ParcelAssignmentSummary(id, parcelId, courierId, type, status, assignedAt, completedAt,
                dispatcherId);
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public ParcelAssignmentType getType() {
        return type;
    }

    public ParcelAssignmentStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }
}
