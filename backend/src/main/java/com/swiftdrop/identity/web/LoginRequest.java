package com.swiftdrop.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 320) String email,
        @NotBlank @Size(max = 72) String password) {

    /** Never includes the email or password. */
    @Override
    public String toString() {
        return "LoginRequest[***]";
    }
}
