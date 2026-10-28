package com.swiftdrop.delivery.api;

/**
 * Origin source of a parcel delivery request, distinguishing direct-customer
 * bookings from merchant-submitted deliveries.
 */
public enum ParcelSource {
    CUSTOMER,
    MERCHANT
}
