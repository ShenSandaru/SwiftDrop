package com.swiftdrop.delivery.domain;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Pickup or drop-off contact and address, embedded twice in {@link Parcel} with {@code pickup_} and
 * {@code dropoff_} column prefixes (D-23). Contains personal data. Request validation (lengths,
 * phone format) happens before an address is built; this class only guards required values.
 */
@Embeddable
public class ParcelAddress {

    @Column(name = "contact_name", nullable = false, length = 200)
    private String contactName;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "line1", nullable = false, length = 200)
    private String line1;

    @Column(name = "line2", length = 200)
    private String line2;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    protected ParcelAddress() {
        // for JPA
    }

    public ParcelAddress(String contactName, String phone, String line1, String line2, String city, UUID zoneId,
            String postalCode) {
        this.contactName = requireText(contactName, "contactName");
        this.phone = requireText(phone, "phone");
        this.line1 = requireText(line1, "line1");
        this.line2 = optionalText(line2);
        this.city = requireText(city, "city");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        this.postalCode = optionalText(postalCode);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public String getContactName() {
        return contactName;
    }

    public String getPhone() {
        return phone;
    }

    public String getLine1() {
        return line1;
    }

    public String getLine2() {
        return line2;
    }

    public String getCity() {
        return city;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public String getPostalCode() {
        return postalCode;
    }

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "ParcelAddress[zoneId=" + zoneId + ", ***]";
    }
}
