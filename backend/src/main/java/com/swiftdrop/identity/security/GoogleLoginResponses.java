package com.swiftdrop.identity.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.swiftdrop.identity.application.GoogleExchangeService;
import com.swiftdrop.identity.application.GoogleIdentity;
import com.swiftdrop.identity.application.GoogleSignInRejectedException;
import com.swiftdrop.identity.application.GoogleSignInService;
import com.swiftdrop.identity.domain.User;

/**
 * Handles the completion of the Google OAuth flow: generates a single-use exchange code on success
 * and redirects to the frontend callback, or redirects with an allowlisted error code on failure (ADR 0005).
 * Bearer tokens are never sent in redirect URLs.
 */
@Component
@Conditional(GoogleLoginConfiguration.ClientIdPresent.class)
class GoogleLoginResponses implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginResponses.class);

    private final GoogleSignInService signIn;
    private final GoogleExchangeService exchangeService;
    private final String frontendBaseUrl;

    GoogleLoginResponses(
            GoogleSignInService signIn,
            GoogleExchangeService exchangeService,
            @Value("${swiftdrop.frontend.base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.signIn = signIn;
        this.exchangeService = exchangeService;
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        endSession(request);
        if (!(authentication.getPrincipal() instanceof OidcUser oidc)) {
            redirectError(response, "oauth_failed");
            return;
        }
        GoogleIdentity identity = new GoogleIdentity(oidc.getSubject(), oidc.getEmail(),
                Boolean.TRUE.equals(oidc.getEmailVerified()), oidc.getFullName());
        User user;
        try {
            user = signIn.signIn(identity);
        } catch (GoogleSignInRejectedException e) {
            log.info("Google sign-in rejected: {}", e.getReason());
            redirectRejected(response, e.getReason());
            return;
        }
        String exchangeCode = exchangeService.createExchangeCode(user.getId());
        redirect(response, "code", exchangeCode);
    }

    /** Invalid state, failed code exchange or an ID token that failed validation. */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        endSession(request);
        String errorCode = "oauth_failed";
        if (exception instanceof OAuth2AuthenticationException oauth2) {
            String error = oauth2.getError().getErrorCode();
            log.info("Google sign-in failed: {}", error);
            if ("access_denied".equalsIgnoreCase(error)) {
                errorCode = "cancelled";
            }
        }
        redirectError(response, errorCode);
    }

    private void redirectRejected(HttpServletResponse response, GoogleSignInRejectedException.Reason reason)
            throws IOException {
        String errorCode = switch (reason) {
            case EMAIL_NOT_VERIFIED -> "unverified_email";
            case EMAIL_ALREADY_REGISTERED -> "conflict_email_exists";
            case ACCOUNT_UNAVAILABLE -> "account_disabled";
            case CONFLICT, INVALID_IDENTITY -> "oauth_failed";
        };
        redirectError(response, errorCode);
    }

    private void redirectError(HttpServletResponse response, String errorCode) throws IOException {
        redirect(response, "error", errorCode);
    }

    private void redirect(HttpServletResponse response, String paramName, String paramValue) throws IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String encodedValue = URLEncoder.encode(paramValue, StandardCharsets.UTF_8);
        String targetUrl = frontendBaseUrl + "/auth/google/callback?" + paramName + "=" + encodedValue;
        response.sendRedirect(targetUrl);
    }

    /** The session held only the authorization request and Google's tokens; neither is needed now. */
    private static void endSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
