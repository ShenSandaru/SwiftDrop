package com.swiftdrop.merchant.web;

import java.util.Objects;
import java.util.UUID;

import com.swiftdrop.merchant.api.MerchantStatus;

/**
 * Public response returned after a merchant application is submitted.
 * Contains no secrets, hashes, or role internals.
 */
public record MerchantApplicationResponse(
        UUID merchantId,
        MerchantStatus status) {

    public MerchantApplicationResponse {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}

