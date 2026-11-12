package com.swiftdrop.merchant.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for merchant registration application (MER-1).
 */
public record MerchantApplicationRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,

        @NotBlank(message = "Business name is required")
        @Size(min = 2, max = 150, message = "Business name must be between 2 and 150 characters")
        String businessName,

        @NotBlank(message = "Business type is required")
        @Size(min = 2, max = 50, message = "Business type must be between 2 and 50 characters")
        String businessType,

        @NotBlank(message = "Business registration number is required")
        @Pattern(regexp = "^[A-Za-z0-9/-]{3,50}$", message = "Business registration number must be 3-50 alphanumeric characters, hyphens, or slashes")
        String businessRegistrationNo,

        @NotBlank(message = "Contact phone is required")
        @Pattern(regexp = "^(\\+94|0)[0-9]{9}$", message = "Contact phone must be a valid Sri Lankan phone number (+94... or 07...)")
        String contactPhone,

        @NotBlank(message = "Business address is required")
        @Size(min = 5, max = 300, message = "Business address must be between 5 and 300 characters")
        String businessAddress,

        @Size(max = 500, message = "Business description must be at most 500 characters")
        String businessDescription,

        @Pattern(regexp = "^$|^(https?://.+)$", message = "Website URL must start with http:// or https://")
        @Size(max = 2048, message = "Website URL must be at most 2048 characters")
        String websiteUrl) {
}

