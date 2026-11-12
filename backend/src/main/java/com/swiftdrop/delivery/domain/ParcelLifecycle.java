package com.swiftdrop.delivery.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * The parcel status transitions, kept in tables that {@link Parcel} consults before every change.
 *
 * <ul>
 *   <li><b>Hub network</b> (ADR 0009 §3), for every parcel with a route: pickup, hub scans and
 *       line-haul legs, delivery attempts, returns, cancellation before pickup, and {@code FAILED}
 *       as an exception after pickup.</li>
 *   <li><b>Legacy</b> (ADR 0006 §1), only for parcels that were picked up without a route, before
 *       the hub network existed: one step at a time to {@code DELIVERED}, {@code FAILED} from
 *       {@code PICKED_UP} to {@code OUT_FOR_DELIVERY}.</li>
 * </ul>
 *
 * Who may make a move, and the route rules, are checked by {@link Parcel}; this class only says
 * which moves exist.
 */
public final class ParcelLifecycle {

    private static final Map<ParcelStatus, Set<ParcelStatus>> HUB = new EnumMap<>(ParcelStatus.class);
    private static final Map<ParcelStatus, Set<ParcelStatus>> LEGACY = new EnumMap<>(ParcelStatus.class);

    static {
        HUB.put(ParcelStatus.CREATED, EnumSet.of(ParcelStatus.PICKED_UP, ParcelStatus.CANCELLED));
        HUB.put(ParcelStatus.PICKED_UP, EnumSet.of(ParcelStatus.AT_HUB, ParcelStatus.FAILED));
        HUB.put(ParcelStatus.AT_HUB, EnumSet.of(ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY,
                ParcelStatus.RETURNING, ParcelStatus.FAILED));
        HUB.put(ParcelStatus.IN_TRANSIT, EnumSet.of(ParcelStatus.AT_HUB, ParcelStatus.FAILED));
        HUB.put(ParcelStatus.OUT_FOR_DELIVERY, EnumSet.of(ParcelStatus.DELIVERED,
                ParcelStatus.DELIVERY_ATTEMPT_FAILED, ParcelStatus.FAILED));
        HUB.put(ParcelStatus.DELIVERY_ATTEMPT_FAILED, EnumSet.of(ParcelStatus.AT_HUB, ParcelStatus.FAILED));
        HUB.put(ParcelStatus.RETURNING, EnumSet.of(ParcelStatus.RETURNED, ParcelStatus.FAILED));

        LEGACY.put(ParcelStatus.PICKED_UP, EnumSet.of(ParcelStatus.IN_TRANSIT, ParcelStatus.FAILED));
        LEGACY.put(ParcelStatus.IN_TRANSIT, EnumSet.of(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED));
        LEGACY.put(ParcelStatus.OUT_FOR_DELIVERY, EnumSet.of(ParcelStatus.DELIVERED, ParcelStatus.FAILED));

        for (ParcelStatus status : ParcelStatus.values()) {
            HUB.putIfAbsent(status, EnumSet.noneOf(ParcelStatus.class));
            LEGACY.putIfAbsent(status, EnumSet.noneOf(ParcelStatus.class));
        }
    }

    private ParcelLifecycle() {
    }

    /**
     * @param legacy whether the parcel follows the ADR 0006 rules (picked up without a route)
     */
    public static boolean canMove(ParcelStatus from, ParcelStatus to, boolean legacy) {
        return table(legacy).get(from).contains(to);
    }

    public static boolean isTerminal(ParcelStatus status) {
        return status.isFinal();
    }

    /** Statuses a parcel can move to next, by anyone; empty for final statuses. */
    public static Set<ParcelStatus> nextStatuses(ParcelStatus from, boolean legacy) {
        return Set.copyOf(table(legacy).get(from));
    }

    private static Map<ParcelStatus, Set<ParcelStatus>> table(boolean legacy) {
        return legacy ? LEGACY : HUB;
    }
}
