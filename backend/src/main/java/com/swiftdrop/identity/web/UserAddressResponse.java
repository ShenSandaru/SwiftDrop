package com.swiftdrop.identity.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.identity.domain.UserAddress;

public record UserAddressResponse(
        UUID id,
        UUID userId,
        String label,
        String recipientName,
        String phoneNumber,
        String streetAddress,
        String city,
        String postalCode,
        boolean isDefault,
        Instant createdAt) {

    public static UserAddressResponse from(UserAddress address) {
        return new UserAddressResponse(
                address.getId(),
                address.getUserId(),
                address.getLabel(),
                address.getRecipientName(),
                address.getPhoneNumber(),
                address.getStreetAddress(),
                address.getCity(),
                address.getPostalCode(),
                address.isDefault(),
                address.getCreatedAt());
    }
}

