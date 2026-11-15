package com.swiftdrop.delivery.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Links two branch hubs (ADR 0011 §3); the order of the two hubs does not matter. */
record HubLinkRequest(@NotNull UUID hubId, @NotNull UUID otherHubId) {
}
