package com.swiftdrop.delivery.application;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.Parcel;

/**
 * The authenticated user reading parcels, with all of their roles. Decides parcel visibility by
 * role and ownership (SYS-2, contract §6); anything not visible is reported as not found.
 */
public record Viewer(UUID userId, Set<ActorRole> roles) {

    public Viewer {
        Objects.requireNonNull(userId, "userId");
        roles = roles == null || roles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(roles));
    }

    public static Viewer of(UUID userId, ActorRole... roles) {
        return new Viewer(userId, Set.of(roles));
    }

    public boolean isAdmin() {
        return roles.contains(ActorRole.ADMIN);
    }

    public boolean isDispatcher() {
        return roles.contains(ActorRole.DISPATCHER);
    }

    public boolean hasDispatchAccess() {
        return isAdmin() || isDispatcher();
    }

    public boolean owns(Parcel parcel) {
        return roles.contains(ActorRole.CUSTOMER) && userId.equals(parcel.getCustomerId());
    }

    public boolean isAssignedCourierOf(Parcel parcel) {
        return roles.contains(ActorRole.COURIER) && userId.equals(parcel.getAssignedCourierId());
    }

    /** Detail and timeline: the owning customer, the assigned courier, dispatchers and admins. */
    public boolean canView(Parcel parcel) {
        return canView(parcel, false);
    }

    /**
     * As above, and also a courier who held a task on the parcel: the parcel changes hands at hubs
     * (ADR 0009 §5), and a courier still sees what they picked up, delivered or returned.
     *
     * @param heldTask whether this caller has or had a courier task on the parcel
     */
    public boolean canView(Parcel parcel, boolean heldTask) {
        return hasDispatchAccess() || owns(parcel) || isCourierOn(parcel, heldTask);
    }

    /** Courier notes may describe the recipient, so customers do not see them. */
    public boolean canSeeNotes(Parcel parcel) {
        return canSeeNotes(parcel, false);
    }

    public boolean canSeeNotes(Parcel parcel, boolean heldTask) {
        return hasDispatchAccess() || isCourierOn(parcel, heldTask);
    }

    /** Whether this caller is a courier who must look up their tasks on the parcel. */
    public boolean isCourier() {
        return roles.contains(ActorRole.COURIER);
    }

    private boolean isCourierOn(Parcel parcel, boolean heldTask) {
        return isAssignedCourierOf(parcel) || (isCourier() && heldTask);
    }

    /** Receipts: the owning customer, dispatchers and admins (ADR 0006 §6). */
    public boolean canSeeReceipt(Parcel parcel) {
        return hasDispatchAccess() || owns(parcel);
    }
}
