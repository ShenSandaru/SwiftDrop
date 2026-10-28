package com.swiftdrop.pricing.api;

/**
 * Standard parcel size categories (D-24).
 *
 * <p>Owned by the pricing module so pricing rules, size surcharges, and fee calculation can define
 * size tiers without introducing circular dependencies to the delivery module (ADR 0003).
 */
public enum ParcelSize {
    SMALL,
    MEDIUM,
    LARGE
}
