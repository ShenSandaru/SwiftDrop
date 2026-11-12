package com.swiftdrop.delivery.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tunable delivery rules (ADR 0009 §3): how many delivery attempts a parcel gets before it is
 * returned to the sender. Startup fails outside the ADR bounds.
 */
@Component
public class DeliveryPolicy {

    static final int MIN_ATTEMPTS = 1;
    static final int MAX_ATTEMPTS = 5;

    private final int maxDeliveryAttempts;

    public DeliveryPolicy(@Value("${swiftdrop.delivery.max-delivery-attempts:3}") int maxDeliveryAttempts) {
        if (maxDeliveryAttempts < MIN_ATTEMPTS || maxDeliveryAttempts > MAX_ATTEMPTS) {
            throw new IllegalStateException("swiftdrop.delivery.max-delivery-attempts must be between "
                    + MIN_ATTEMPTS + " and " + MAX_ATTEMPTS);
        }
        this.maxDeliveryAttempts = maxDeliveryAttempts;
    }

    /** The ADR default of three attempts. */
    public static DeliveryPolicy defaults() {
        return new DeliveryPolicy(3);
    }

    public int maxDeliveryAttempts() {
        return maxDeliveryAttempts;
    }
}
