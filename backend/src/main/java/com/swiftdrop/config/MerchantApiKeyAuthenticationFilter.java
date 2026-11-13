package com.swiftdrop.config;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import com.swiftdrop.merchant.api.AuthenticatedMerchant;
import com.swiftdrop.merchant.api.MerchantAuthentication;

/**
 * Authenticates {@code X-Api-Key} on the merchant API chain (ADR 0008 §5). A valid key sets an
 * {@link AuthenticatedMerchant} principal with the single authority {@code MERCHANT_API_KEY}; an
 * invalid key is answered at once with the uniform 401. Without the header the request continues
 * unauthenticated and is refused by the chain's authorization rule with the same 401.
 */
class MerchantApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Api-Key";

    private final MerchantAuthentication merchants;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    MerchantApiKeyAuthenticationFilter(MerchantAuthentication merchants) {
        this.merchants = merchants;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER);
        if (apiKey != null) {
            Optional<AuthenticatedMerchant> merchant = merchants.authenticate(apiKey);
            if (merchant.isEmpty()) {
                UNAUTHORIZED.commence(request, response, null);
                return;
            }
            var authentication = UsernamePasswordAuthenticationToken.authenticated(merchant.get(), null,
                    List.of(new SimpleGrantedAuthority(AuthenticatedMerchant.AUTHORITY)));
            SecurityContext context = contextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            contextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }

    /**
     * The one response for every API-key failure: missing, malformed, unknown, wrong, revoked, or a
     * merchant that is not approved (ADR 0008 §4).
     */
    static final AuthenticationEntryPoint UNAUTHORIZED = MerchantApiKeyAuthenticationFilter::unauthorized;

    private static void unauthorized(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "ApiKey");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                + "\"detail\":\"A valid X-Api-Key header is required\"}");
    }
}
