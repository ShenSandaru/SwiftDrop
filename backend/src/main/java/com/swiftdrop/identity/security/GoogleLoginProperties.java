package com.swiftdrop.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google OAuth 2.0 client credentials. Bound only when a client ID is set; startup then fails if
 * the secret is missing.
 *
 * @param clientId     from {@code SWIFTDROP_GOOGLE_CLIENT_ID}
 * @param clientSecret from {@code SWIFTDROP_GOOGLE_CLIENT_SECRET}
 */
@ConfigurationProperties("swiftdrop.security.google")
public record GoogleLoginProperties(String clientId, String clientSecret) {

    static final String CLIENT_ID_PROPERTY = "swiftdrop.security.google.client-id";

    public GoogleLoginProperties {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException(CLIENT_ID_PROPERTY + " (SWIFTDROP_GOOGLE_CLIENT_ID) must be set");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("swiftdrop.security.google.client-secret"
                    + " (SWIFTDROP_GOOGLE_CLIENT_SECRET) must be set when a Google client ID is set");
        }
    }

    /** Never includes the secret. */
    @Override
    public String toString() {
        return "GoogleLoginProperties[clientId=" + clientId + "]";
    }
}
