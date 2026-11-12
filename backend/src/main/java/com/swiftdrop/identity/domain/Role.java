package com.swiftdrop.identity.domain;

/**
 * System roles for authorization and access control.
 * Exactly five active roles are stored in the {@code roles} table:
 * {@link #ADMIN}, {@link #DISPATCHER}, {@link #COURIER}, {@link #MERCHANT}, {@link #CUSTOMER}.
 */
public enum Role {
    CUSTOMER,
    MERCHANT,
    COURIER,
    DISPATCHER,
    ADMIN
}
