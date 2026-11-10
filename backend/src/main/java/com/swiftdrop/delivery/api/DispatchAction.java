package com.swiftdrop.delivery.api;

/**
 * A step a dispatcher may take next on a parcel (ADR 0009 §3, §5, §6). {@code delivery} works these
 * out from its own rules, so the dispatch board never repeats them.
 */
public enum DispatchAction {
    /** Assign or reassign the pickup courier. */
    ASSIGN_PICKUP,
    /** Cancel before pickup. */
    CANCEL,
    /** Scan the parcel in at the hub it is expected at. */
    SCAN_AT_HUB,
    /** Send it from its current hub to the next hub of its route. */
    SEND_TO_NEXT_HUB,
    /** Assign or reassign the courier for the next delivery attempt. */
    ASSIGN_DELIVERY,
    /** Start the return after the last allowed attempt or a final refusal. */
    START_RETURN,
    /** Assign or reassign the return courier. */
    ASSIGN_RETURN,
    /** End the parcel as lost or damaged. */
    MARK_FAILED
}
