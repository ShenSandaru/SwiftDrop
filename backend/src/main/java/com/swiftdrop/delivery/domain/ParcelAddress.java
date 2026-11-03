package com.swiftdrop.delivery.domain;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Pickup or drop-off contact and address, embedded twice in {@link Parcel} with {@code pickup_} and
 * {@code dropoff_} column prefixes (D-23). Contains personal data. Enforces the column limits and the
 * phone format itself, because merchant orders reach it without HTTP validation; error messages
 * name the field, never the value.
 */
@Embeddable
public class ParcelAddress {

    /** E.164, for example {@code +94771234567} (contract §3.1). */
    public static final Pattern PHONE = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    public static final int MAX_NAME_LENGTH = 200;
    public static final int MAX_LINE_LENGTH = 200;
    public static final int MAX_CITY_LENGTH = 100;
    public static final int MAX_POSTAL_CODE_LENGTH = 10;

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
        this.contactName = requireText(contactName, "contactName", MAX_NAME_LENGTH);
        this.phone = requireText(phone, "phone", 20);
        if (!PHONE.matcher(this.phone).matches()) {
            throw new IllegalArgumentException("phone must be in international format, for example +94771234567");
        }
        this.line1 = requireText(line1, "line1", MAX_LINE_LENGTH);
        this.line2 = optionalText(line2, "line2", MAX_LINE_LENGTH);
        this.city = requireText(city, "city", MAX_CITY_LENGTH);
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        this.postalCode = optionalText(postalCode, "postalCode", MAX_POSTAL_CODE_LENGTH);
    }

    private static String requireText(String value, String name, int maxLength) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return checkLength(trimmed, name, maxLength);
    }

    private static String optionalText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return checkLength(value.trim(), name, maxLength);
    }

    private static String checkLength(String value, String name, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must be at most " + maxLength + " characters");
        }
        return value;
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
