package com.swiftdrop.identity.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating staff accounts (COURIER or DISPATCHER_ADMIN) under SD-102.
 */
public record CreateStaffUserRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        String role
) {
    public CreateStaffUserRequest {
        fullName = fullName == null ? null : fullName.trim();
        email = email == null ? null : email.trim();
        role = role == null ? null : role.trim();
    }

    @Override
    public String toString() {
        return "CreateStaffUserRequest[***]";
    }
}
