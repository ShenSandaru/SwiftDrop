package com.swiftdrop.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects requests on {@code pricingAndZonesSecurity} (ADR 0008 addendum) that present both a
 * merchant API key and a bearer token: a caller must use exactly one, and presenting both is a
 * malformed request, not a credential failure, so it is refused before either authentication
 * mechanism runs rather than letting filter order decide a winner.
 */
class AmbiguousCredentialsFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(MerchantApiKeyAuthenticationFilter.HEADER);
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean hasApiKey = apiKey != null && !apiKey.isBlank();
        boolean hasBearerToken = authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
        if (hasApiKey && hasBearerToken) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400,"
                    + "\"detail\":\"Provide either a bearer token or an X-Api-Key header, not both\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
