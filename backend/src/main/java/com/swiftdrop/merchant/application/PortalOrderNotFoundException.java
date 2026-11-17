package com.swiftdrop.merchant.application;

/**
 * No order with this ID for the calling merchant. Another merchant's order answers exactly the same, so order IDs
 * cannot be enumerated (404, never 403).
 */
public class PortalOrderNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PortalOrderNotFoundException() {
        super("Order not found");
    }
}
