package com.swiftdrop.delivery.api;

/**
 * Creates parcels for merchant orders (MER-2). Used by {@code merchant}; customer bookings use
 * delivery's own endpoint and the same internal rules.
 */
public interface ParcelCreation {

    /**
     * Creates a parcel in {@link ParcelStatus#CREATED} with its fee, first history entry and
     * {@code CREATED} event, all in one transaction. Idempotent per merchant and order reference
     * (D-25): a repeated reference returns the existing parcel with
     * {@link CreatedParcel#newlyCreated()} {@code false} and emits no second event.
     *
     * @throws IllegalArgumentException if a field is missing or invalid
     */
    CreatedParcel createMerchantParcel(NewMerchantParcel order);

    /**
     * Books a walk-in customer parcel at a hub counter and immediately receives it at {@code hubId}
     * in {@link ParcelStatus#AT_HUB}, recording both {@code CREATED} and {@code AT_HUB} events in
     * one transaction.
     *
     * @throws IllegalArgumentException if a field is missing or invalid
     */
    CreatedParcel createWalkInParcel(NewWalkInParcel walkIn);
}
