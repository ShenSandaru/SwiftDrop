package com.swiftdrop.pricing.web;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.swiftdrop.pricing.api.ParcelSize;

/** Fee estimate request (contract §4.3); the same inputs delivery uses when it books the parcel. */
record PriceQuoteRequest(
        @NotNull UUID pickupZoneId,
        @NotNull UUID dropoffZoneId,
        @NotNull ParcelSize size,
        @NotNull @Min(1) @Max(30_000) Integer weightGrams) {
}
