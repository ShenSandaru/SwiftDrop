package com.swiftdrop.identity.application;

/**
 * Claims of a Google ID token that Spring Security has already validated (signature, issuer,
 * audience, expiry and nonce). Never build one from client-submitted data.
 *
 * @param subject       the stable {@code sub} claim, the key of the account link
 * @param email         the {@code email} claim, may be {@code null}
 * @param emailVerified whether the {@code email_verified} claim is {@code true}
 * @param name          the {@code name} claim, may be {@code null}
 */
public record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {

    /** Leaves out personal data. */
    @Override
    public String toString() {
        return "GoogleIdentity[emailVerified=" + emailVerified + "]";
    }
}
