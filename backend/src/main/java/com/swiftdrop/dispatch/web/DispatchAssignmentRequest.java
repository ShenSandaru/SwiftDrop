package com.swiftdrop.dispatch.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import com.swiftdrop.delivery.api.ParcelAssignmentType;

record DispatchAssignmentRequest(
        @NotNull UUID parcelId,
        @NotNull UUID courierId,
        @NotNull ParcelAssignmentType type
) {
}
