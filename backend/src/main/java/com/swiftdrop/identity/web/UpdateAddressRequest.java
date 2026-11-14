package com.swiftdrop.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAddressRequest(
        @NotBlank(message = "Label cannot be blank")
        @Size(max = 50, message = "Label must be at most 50 characters")
        String label,

        @NotBlank(message = "Recipient name cannot be blank")
        @Size(max = 200, message = "Recipient name must be at most 200 characters")
        String recipientName,

        @NotBlank(message = "Phone number cannot be blank")
        @Size(max = 30, message = "Phone number must be at most 30 characters")
        String phoneNumber,

        @NotBlank(message = "Street address cannot be blank")
        @Size(max = 255, message = "Street address must be at most 255 characters")
        String streetAddress,

        @NotBlank(message = "City cannot be blank")
        @Size(max = 100, message = "City must be at most 100 characters")
        String city,

        @Size(max = 20, message = "Postal code must be at most 20 characters")
        String postalCode,

        boolean isDefault) {
}

