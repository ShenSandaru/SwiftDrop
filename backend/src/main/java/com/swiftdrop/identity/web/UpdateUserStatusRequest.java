package com.swiftdrop.identity.web;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for enabling or disabling a user account under SD-102.
 */
public record UpdateUserStatusRequest(
        @NotNull(message = "enabled status must not be null")
        Boolean enabled
) {}
