package com.swiftdrop.delivery.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelForbiddenException;
import com.swiftdrop.delivery.api.Staff;
import com.swiftdrop.delivery.domain.HubScope;
import com.swiftdrop.delivery.domain.Parcel;

/**
 * Decides whether staff may act on a parcel and whether a courier may be given it (ADR 0012 §3, §4).
 * Checked inside the transaction that locks the parcel, so the answer matches the state it changes.
 */
@Component
class HubAuthority {

    private final StaffScopes scopes;
    private final HubNetwork network;

    HubAuthority(StaffScopes scopes, HubNetwork network) {
        this.scopes = scopes;
        this.network = network;
    }

    /** @throws ParcelForbiddenException if the parcel is not in the hands of one of the staff member's hubs */
    void requireCanAct(Parcel parcel, Staff staff) {
        Objects.requireNonNull(staff, "staff");
        if (!staff.admin()) {
            requireCanAct(parcel, scopes.scopeOf(staff.userId()));
        }
    }

    /** @return the staff member's scope, for the courier check that follows */
    HubScope requireCanAct(Parcel parcel, UUID staffUserId) {
        HubScope scope = scopes.scopeOf(Objects.requireNonNull(staffUserId, "staffUserId"));
        requireCanAct(parcel, scope);
        return scope;
    }

    private void requireCanAct(Parcel parcel, HubScope scope) {
        if (!parcel.accessFor(scope, pickupBranch(parcel)).canAct()) {
            throw new ParcelForbiddenException("This parcel is handled by another hub");
        }
    }

    /**
     * A courier with hubs may only be given parcels their hub is responsible for; one without hubs
     * only by staff who work everywhere (ADR 0012 §4).
     *
     * @param assignerScope the scope of the dispatcher giving the task
     * @throws ParcelConflictException if the courier may not be given this parcel
     */
    void requireCourierMayHandle(Parcel parcel, UUID courierUserId, HubScope assignerScope) {
        HubScope courier = scopes.scopeOf(Objects.requireNonNull(courierUserId, "courierUserId"));
        if (courier.everywhere()) {
            return;
        }
        if (courier.isEmpty()) {
            if (!assignerScope.everywhere()) {
                throw new ParcelConflictException("The courier is not assigned to a hub");
            }
            return;
        }
        if (!courier.includes(parcel.responsibleHubId(pickupBranch(parcel)))) {
            throw new ParcelConflictException("The courier does not work for this parcel's hub");
        }
    }

    private UUID pickupBranch(Parcel parcel) {
        return network.branchHubId(parcel.getPickup().getZoneId()).orElse(null);
    }
}
