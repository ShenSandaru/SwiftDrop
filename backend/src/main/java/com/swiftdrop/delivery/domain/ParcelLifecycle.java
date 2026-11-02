package com.swiftdrop.delivery.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * The parcel status transitions of ADR 0006 §1, kept in one table: forward one step at a time,
 * {@code FAILED} from {@code PICKED_UP} through {@code OUT_FOR_DELIVERY}, and {@code DELIVERED} and
 * {@code FAILED} terminal. {@link Parcel} asks this table before every status change.
 */
public final class ParcelLifecycle {

    private static final Map<ParcelStatus, Set<ParcelStatus>> ALLOWED = new EnumMap<>(ParcelStatus.class);

    static {
        ALLOWED.put(ParcelStatus.CREATED, EnumSet.of(ParcelStatus.PICKED_UP));
        ALLOWED.put(ParcelStatus.PICKED_UP, EnumSet.of(ParcelStatus.IN_TRANSIT, ParcelStatus.FAILED));
        ALLOWED.put(ParcelStatus.IN_TRANSIT, EnumSet.of(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED));
        ALLOWED.put(ParcelStatus.OUT_FOR_DELIVERY, EnumSet.of(ParcelStatus.DELIVERED, ParcelStatus.FAILED));
        ALLOWED.put(ParcelStatus.DELIVERED, EnumSet.noneOf(ParcelStatus.class));
        ALLOWED.put(ParcelStatus.FAILED, EnumSet.noneOf(ParcelStatus.class));
    }

    private ParcelLifecycle() {
    }

    public static boolean canMove(ParcelStatus from, ParcelStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    public static boolean isTerminal(ParcelStatus status) {
        return ALLOWED.get(status).isEmpty();
    }

    /** Statuses a parcel can move to next; empty for terminal statuses. */
    public static Set<ParcelStatus> nextStatuses(ParcelStatus from) {
        return Set.copyOf(ALLOWED.get(from));
    }
}
