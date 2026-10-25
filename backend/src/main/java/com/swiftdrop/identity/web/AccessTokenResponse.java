package com.swiftdrop.identity.web;

/** OAuth 2.0-style token response; {@code expiresIn} is in seconds. */
public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {

    /** Never includes the token. */
    @Override
    public String toString() {
        return "AccessTokenResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
