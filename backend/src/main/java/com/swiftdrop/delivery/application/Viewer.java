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
        return roles.contains(ActorRole.DISPATCHER_ADMIN);
    }

    public boolean owns(Parcel parcel) {
        return roles.contains(ActorRole.CUSTOMER) && userId.equals(parcel.getCustomerId());
    }

    public boolean isAssignedCourierOf(Parcel parcel) {
        return roles.contains(ActorRole.COURIER) && userId.equals(parcel.getAssignedCourierId());
    }

    /** Detail and timeline: the owning customer, the assigned courier, dispatchers/admins. */
    public boolean canView(Parcel parcel) {
        return isAdmin() || owns(parcel) || isAssignedCourierOf(parcel);
    }

    /** Courier notes may describe the recipient, so customers do not see them. */
    public boolean canSeeNotes(Parcel parcel) {
        return isAdmin() || isAssignedCourierOf(parcel);
    }

    /** Receipts: the owning customer and dispatchers/admins (ADR 0006 §6). */
    public boolean canSeeReceipt(Parcel parcel) {
        return isAdmin() || owns(parcel);
    }
}
