package com.swiftdrop.merchant.domain;

import com.swiftdrop.merchant.api.MerchantStatus;

/**
 * Thrown when an illegal lifecycle transition is attempted on a merchant account (Phase 3).
 * Extends {@link IllegalStateException} to maintain domain exception hierarchy.
 */
public class IllegalStateTransitionException extends IllegalStateException {

    private final MerchantStatus fromStatus;
    private final MerchantStatus toStatus;

    public IllegalStateTransitionException(MerchantStatus fromStatus, MerchantStatus toStatus) {
        super(String.format("Cannot transition merchant status from %s to %s", fromStatus, toStatus));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public MerchantStatus getFromStatus() {
        return fromStatus;
    }

    public MerchantStatus getToStatus() {
        return toStatus;
    }
}
