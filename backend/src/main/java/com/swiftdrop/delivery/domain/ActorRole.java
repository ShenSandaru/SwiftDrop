package com.swiftdrop.delivery.domain;

/**
 * Role in which an actor changed a parcel, recorded in the status history. Mirrors the role names
 * of {@code identity} without importing its internal {@code Role} (ADR 0003).
 */
public enum ActorRole {
    CUSTOMER,
    MERCHANT,
    COURIER,
    DISPATCHER_ADMIN
}
