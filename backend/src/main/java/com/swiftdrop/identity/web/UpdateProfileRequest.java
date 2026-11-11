package com.swiftdrop.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 200, message = "Full name must be at most 200 characters")
        String fullName,

        @Size(max = 30, message = "Phone number must be at most 30 characters")
        String phoneNumber
) {
    public UpdateProfileRequest(String fullName) {
        this(fullName, null);
    }
}
