package com.swiftdrop.delivery.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.swiftdrop.delivery.application.Booking;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * Customer booking (CUS-2). There is deliberately no fee, owner or status field: unknown JSON fields
 * are ignored, and the fee is always recalculated by pricing.
 */
record CreateParcelRequest(
        @NotNull @Valid AddressRequest pickup,
        @NotNull @Valid AddressRequest dropoff,
        @NotNull ParcelSize size,
        @NotNull @Min(1) @Max(30_000) Integer weightGrams,
        @Size(max = 200) String description) {

    Booking toBooking() {
        return new Booking(pickup.toContact(), dropoff.toContact(), size, weightGrams, description);
    }

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "CreateParcelRequest[size=" + size + ", weightGrams=" + weightGrams + ", ***]";
    }
}
