package com.swiftdrop.delivery.api;

import java.util.UUID;

/**
 * Pickup or drop-off contact and address of a parcel. Contains personal data (name, phone,
 * address lines), so it never appears in events, public tracking or logs.
 *
 * <p>Field validation (lengths, phone format) is done by {@code delivery} when the parcel is
 * created, so callers get the same rules for customer and merchant parcels.
 *
 * @param line2 optional second address line
 * @param postalCode optional postal code
 */
public record DeliveryContact(
        String contactName,
        String phone,
        String line1,
        String line2,
        String city,
        UUID zoneId,
        String postalCode
) {

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "DeliveryContact[zoneId=" + zoneId + ", ***]";
    }
}
