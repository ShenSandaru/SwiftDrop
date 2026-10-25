package com.swiftdrop.identity.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import com.swiftdrop.identity.application.GoogleIdentity;
import com.swiftdrop.identity.application.GoogleSignInRejectedException;
import com.swiftdrop.identity.application.GoogleSignInService;
import com.swiftdrop.identity.web.AccessTokenResponse;

/**
 * Responses of the Google callback: the same token body as {@code POST /api/v1/auth/tokens} on
 * success, an RFC 9457 problem otherwise. Error details never contain tokens or profile data.
 */
@Component
@Conditional(GoogleLoginConfiguration.ClientIdPresent.class)
class GoogleLoginResponses implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginResponses.class);

    private final GoogleSignInService signIn;
    private final AccessTokenIssuer tokens;
    private final JsonMapper json;

    GoogleLoginResponses(GoogleSignInService signIn, AccessTokenIssuer tokens, JsonMapper json) {
        this.signIn = signIn;
        this.tokens = tokens;
        this.json = json;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        endSession(request);
        if (!(authentication.getPrincipal() instanceof OidcUser oidc)) {
            problem(response, HttpStatus.UNAUTHORIZED, "Google sign-in failed");
            return;
        }
        GoogleIdentity identity = new GoogleIdentity(oidc.getSubject(), oidc.getEmail(),
                Boolean.TRUE.equals(oidc.getEmailVerified()), oidc.getFullName());
        AccessTokenIssuer.AccessToken token;
        try {
            token = tokens.issue(signIn.signIn(identity));
        } catch (GoogleSignInRejectedException e) {
            log.info("Google sign-in rejected: {}", e.getReason());
            rejected(response, e.getReason());
            return;
        }
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        json.writeValue(response.getOutputStream(),
                new AccessTokenResponse(token.value(), "Bearer", token.lifetime().toSeconds()));
    }

    /** Invalid state, failed code exchange or an ID token that failed validation. */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        endSession(request);
        if (exception instanceof OAuth2AuthenticationException oauth2) {
            log.info("Google sign-in failed: {}", oauth2.getError().getErrorCode());
        }
        problem(response, HttpStatus.UNAUTHORIZED, "Google sign-in failed");
    }

    private void rejected(HttpServletResponse response, GoogleSignInRejectedException.Reason reason)
            throws IOException {
        switch (reason) {
            case EMAIL_NOT_VERIFIED -> problem(response, HttpStatus.FORBIDDEN,
                    "The Google account has no verified email address");
            case EMAIL_ALREADY_REGISTERED -> problem(response, HttpStatus.CONFLICT,
                    "An account with this email address already exists. Sign in with your email and"
                            + " password; linking a Google account to it is not supported yet");
            case ACCOUNT_UNAVAILABLE -> problem(response, HttpStatus.FORBIDDEN,
                    "Google sign-in is not available for this account");
            case CONFLICT -> problem(response, HttpStatus.CONFLICT,
                    "Google sign-in could not be completed; try again");
            case INVALID_IDENTITY -> problem(response, HttpStatus.UNAUTHORIZED, "Google sign-in failed");
        }
    }

    private void problem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        json.writeValue(response.getOutputStream(), body);
    }

    /** The session held only the authorization request and Google's tokens; neither is needed now. */
    private static void endSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
