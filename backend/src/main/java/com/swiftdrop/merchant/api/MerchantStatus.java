package com.swiftdrop.merchant.api;

/**
 * Status lifecycle of a merchant business account (MER-1, docs/merchant-contracts.md).
 * Represents the state machine for merchant approval and operational lifecycle.
 */
public enum MerchantStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED;

    /**
     * Determines whether a transition from this state to {@code target} is permitted
     * per the agreed domain state machine:
     * <ul>
     *   <li>PENDING &rarr; APPROVED, REJECTED</li>
     *   <li>APPROVED &rarr; SUSPENDED</li>
     *   <li>REJECTED &rarr; PENDING (resubmission)</li>
     *   <li>SUSPENDED &rarr; APPROVED (reinstatement)</li>
     * </ul>
     */
    public boolean canTransitionTo(MerchantStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == APPROVED || target == REJECTED;
            case APPROVED -> target == SUSPENDED;
            case REJECTED -> target == PENDING;
            case SUSPENDED -> target == APPROVED;
        };
    }
}

