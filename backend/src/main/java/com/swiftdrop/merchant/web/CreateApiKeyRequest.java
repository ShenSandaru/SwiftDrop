package com.swiftdrop.merchant.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.swiftdrop.merchant.domain.MerchantApiKey;

/** Body of {@code POST /api/v1/merchants/me/api-keys}. */
public record CreateApiKeyRequest(
        @NotBlank @Size(max = MerchantApiKey.MAX_LABEL_LENGTH) String label) {
}
