package com.swiftdrop.identity.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public registration. There is deliberately no role field; unknown JSON fields are ignored. */
public record RegisterUserRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {

    public RegisterUserRequest {
        fullName = fullName == null ? null : fullName.trim();
        email = email == null ? null : email.trim();
    }

    /** Never includes personal data or the password. */
    @Override
    public String toString() {
        return "RegisterUserRequest[***]";
    }
}
