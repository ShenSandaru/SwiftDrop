package com.swiftdrop.merchant.web;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.swiftdrop.delivery.api.NewMerchantParcel;

/**
 * Body of {@code POST /api/v1/merchant-orders} (contract §4.3): the customer booking body plus the
 * merchant's own order reference. There is deliberately no merchant, fee or status field; unknown JSON
 * fields are ignored and the merchant always comes from the API key.
 *
 * @param merchantOrderReference the merchant's order ID, unique per merchant (D-25); letters, digits
 *        and {@code . _ : -}, so it can be used in the URL of {@code GET /api/v1/merchant-orders/{ref}}
 * @param size {@code SMALL}, {@code MEDIUM} or {@code LARGE}, checked by delivery
 */
record MerchantOrderRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = MerchantOrderRequest.REFERENCE) String merchantOrderReference,
        @NotNull @Valid OrderAddressRequest pickup,
        @NotNull @Valid OrderAddressRequest dropoff,
        @NotBlank String size,
        @NotNull @Min(1) @Max(30_000) Integer weightGrams,
        @Size(max = 200) String description) {

    static final String REFERENCE = "^[A-Za-z0-9][A-Za-z0-9._:-]*$";

    /** @throws IllegalArgumentException if the size is unknown */
    NewMerchantParcel toNewParcel(UUID merchantId) {
        return NewMerchantParcel.withSizeName(merchantId, merchantOrderReference, pickup.toContact(),
                dropoff.toContact(), size, weightGrams, description);
    }

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "MerchantOrderRequest[merchantOrderReference=" + merchantOrderReference + ", size=" + size
                + ", weightGrams=" + weightGrams + ", ***]";
    }
}
