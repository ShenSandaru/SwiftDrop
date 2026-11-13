package com.swiftdrop.notification.web;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extracts authenticated caller identity from verified JWT tokens in the notification module.
 * The token subject is verified by Spring Security to be a valid user UUID string.
 */
final class Callers {

    private Callers() {
    }

    /**
     * Extracts the recipient user UUID from the verified JWT 'sub' claim.
     */
    static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

