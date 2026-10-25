package com.swiftdrop.identity.application;

/** A validated Google identity that cannot be used to sign in; see {@link Reason}. */
public class GoogleSignInRejectedException extends RuntimeException {

    public enum Reason {
        /** The ID token lacks a usable subject or email address. */
        INVALID_IDENTITY,
        /** A new account needs an email address that Google states is verified. */
        EMAIL_NOT_VERIFIED,
        /** An account with this email exists but is not linked to this Google identity. */
        EMAIL_ALREADY_REGISTERED,
        /** The linked account is disabled or no longer exists. */
        ACCOUNT_UNAVAILABLE,
        /** A concurrent sign-in or registration created a conflicting row. */
        CONFLICT
    }

    private final Reason reason;

    GoogleSignInRejectedException(Reason reason) {
        super("Google sign-in rejected: " + reason);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
