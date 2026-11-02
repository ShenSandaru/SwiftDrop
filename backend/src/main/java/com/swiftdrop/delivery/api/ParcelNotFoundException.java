package com.swiftdrop.delivery.api;

/**
 * The parcel does not exist, or the caller may not see it. Callers map it to 404 and never
 * distinguish the two cases, so parcel IDs cannot be probed (ADR 0006 §7).
 */
public class ParcelNotFoundException extends RuntimeException {

    public ParcelNotFoundException() {
        super("Parcel not found");
    }
}
