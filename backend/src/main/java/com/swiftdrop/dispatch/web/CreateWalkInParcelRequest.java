package com.swiftdrop.dispatch.web;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Walk-in customer booking created at a hub counter by a dispatcher or administrator. */
record CreateWalkInParcelRequest(
        UUID hubId,
        @NotNull @Valid WalkInAddressRequest pickup,
        @NotNull @Valid WalkInAddressRequest dropoff,
        @NotBlank String size,
        @NotNull @Min(1) @Max(30_000) Integer weightGrams,
        @Size(max = 200) String description) {

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "CreateWalkInParcelRequest[hubId=" + hubId + ", size=" + size + ", weightGrams=" + weightGrams + ", ***]";
    }
}

