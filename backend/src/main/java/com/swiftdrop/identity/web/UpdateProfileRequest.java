package com.swiftdrop.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request to update the authenticated customer's own profile information. */
public record UpdateProfileRequest(
        @NotBlank(message = "Full name cannot be blank")
        @Size(max = 200, message = "Full name must not exceed 200 characters")
        String fullName,

        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "Phone must be in international format, for example +94771234567")
        String phoneNumber
) {
    public UpdateProfileRequest {
        fullName = fullName == null ? null : fullName.trim();
        phoneNumber = phoneNumber == null ? null : phoneNumber.trim();
    }
}


