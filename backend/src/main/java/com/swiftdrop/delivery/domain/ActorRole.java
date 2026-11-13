package com.swiftdrop.delivery.domain;

/**
 * Role in which an actor changed a parcel, recorded in the status history. Mirrors the role names
 * of {@code identity} without importing its internal {@code Role} (ADR 0003).
 */
public enum ActorRole {
    CUSTOMER,
    MERCHANT,
    COURIER,
    DISPATCHER,
    ADMIN,

    /**
     * @deprecated Retained exclusively for historical audit compatibility and deserialization
     *             of past {@code parcel_status_history} records. Must not be used for new operations
     *             or active authorization.
     */
    @Deprecated
    DISPATCHER_ADMIN;

    /**
     * Returns true if this role represents an active application role rather than a legacy audit value.
     */
    public boolean isActive() {
        return this != DISPATCHER_ADMIN;
    }
}
