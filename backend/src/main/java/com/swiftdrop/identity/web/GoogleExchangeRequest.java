package com.swiftdrop.identity.web;

import jakarta.validation.constraints.NotBlank;

public record GoogleExchangeRequest(
        @NotBlank String code) {

    /** Never includes the raw exchange code in logs or string representations. */
    @Override
    public String toString() {
        return "GoogleExchangeRequest[***]";
    }
}
