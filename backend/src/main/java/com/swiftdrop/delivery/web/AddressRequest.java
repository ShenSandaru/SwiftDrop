package com.swiftdrop.delivery.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.swiftdrop.delivery.api.DeliveryContact;

/** Pickup or drop-off contact and address in a booking request (contract §4.3). Personal data. */
record AddressRequest(
        @NotBlank @Size(max = 200) String contactName,
        @NotBlank @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String phone,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 100) String city,
        @NotNull UUID zoneId,
        @Size(max = 10) String postalCode) {

    DeliveryContact toContact() {
        return new DeliveryContact(contactName, phone, line1, line2, city, zoneId, postalCode);
    }

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "AddressRequest[zoneId=" + zoneId + ", ***]";
    }
}
