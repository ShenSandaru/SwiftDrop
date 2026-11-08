package com.swiftdrop.merchant.web;

import java.util.UUID;

/**
 * Response payload returned upon rotating the signing secret of a webhook endpoint (Phase 4).
 * Returns the newly generated secret once, along with the 4-character hint.
 */
public record RotateSecretResponse(
        UUID endpointId,
        String secret,
        String secretHint
) {}

