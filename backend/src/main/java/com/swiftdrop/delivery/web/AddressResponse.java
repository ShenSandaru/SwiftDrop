package com.swiftdrop.delivery.web;

import java.util.UUID;

import com.swiftdrop.delivery.domain.ParcelAddress;

/** A parcel address in detail and receipt responses. Personal data; only for entitled viewers. */
record AddressResponse(String contactName, String phone, String line1, String line2, String city, UUID zoneId,
        String postalCode) {

    static AddressResponse from(ParcelAddress address) {
        return new AddressResponse(address.getContactName(), address.getPhone(), address.getLine1(),
                address.getLine2(), address.getCity(), address.getZoneId(), address.getPostalCode());
    }

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "AddressResponse[zoneId=" + zoneId + ", ***]";
    }
}
