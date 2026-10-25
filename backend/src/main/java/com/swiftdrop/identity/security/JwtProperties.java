package com.swiftdrop.identity.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Signing settings for access tokens. Startup fails if they are missing or too weak.
 *
 * @param secret         HMAC-SHA256 key, at least 32 bytes (from {@code SWIFTDROP_JWT_SECRET})
 * @param issuer         value of the {@code iss} claim, checked on every request
 * @param accessTokenTtl lifetime of an access token
 */
@ConfigurationProperties("swiftdrop.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("swiftdrop.security.jwt.secret (SWIFTDROP_JWT_SECRET)"
                    + " must be set to at least " + MIN_SECRET_BYTES + " bytes");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("swiftdrop.security.jwt.issuer must be set");
        }
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalStateException("swiftdrop.security.jwt.access-token-ttl must be positive");
        }
    }

    /** Never includes the secret. */
    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl + "]";
    }
}
