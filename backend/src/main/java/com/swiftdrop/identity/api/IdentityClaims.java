package com.swiftdrop.identity.api;

/**
 * Claims of the access tokens issued by the identity module. The subject is the user ID.
 */
public final class IdentityClaims {

    /** List of role names, for example {@code ["CUSTOMER"]}. */
    public static final String ROLES = "roles";

    private IdentityClaims() {
    }
}
