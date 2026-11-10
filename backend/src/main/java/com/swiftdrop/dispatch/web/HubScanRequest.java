package com.swiftdrop.dispatch.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** The hub a parcel is scanned in at (ADR 0009 §3). */
record HubScanRequest(@NotNull UUID hubId) {
}
