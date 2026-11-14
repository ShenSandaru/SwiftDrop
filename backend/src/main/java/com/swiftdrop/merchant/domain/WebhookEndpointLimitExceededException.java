package com.swiftdrop.merchant.domain;

/**
 * Thrown when a merchant attempts to configure more webhook endpoints than permitted by policy (Phase 4).
 */
public class WebhookEndpointLimitExceededException extends RuntimeException {

    private final int maxLimit;

    public WebhookEndpointLimitExceededException(int maxLimit) {
        super(String.format("Merchant webhook endpoint limit reached (maximum %d endpoints)", maxLimit));
        this.maxLimit = maxLimit;
    }

    public int getMaxLimit() {
        return maxLimit;
    }
}

