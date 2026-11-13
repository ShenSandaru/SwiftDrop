package com.swiftdrop.config;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.swiftdrop.identity.api.IdentityClaims;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

/**
 * Bearer-token security for the REST API. The {@code JwtDecoder} comes from the identity module.
 * Active tokens are validated for cryptographic integrity, expiration, and user account status
 * (SEC-01) via the public {@link UserDirectory} facade before granting access. Roles become
 * {@code ROLE_<name>} authorities from the token's roles claim, for URL rules here or
 * {@code @PreAuthorize} on endpoints.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

    private final UserDirectory userDirectory;

    SecurityConfiguration(UserDirectory userDirectory) {
        this.userDirectory = userDirectory;
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/v1/users", "/api/v1/auth/tokens", "/api/v1/auth/google/exchanges", "/api/v1/merchants").permitAll()
                        // Public tracking by tracking number (CUS-6); delivery returns no personal data there
                        .requestMatchers(HttpMethod.GET, "/api/v1/tracked-parcels/**").permitAll()
                        // AI assistant (ADR 0010): its tools return only public data, like tracking
                        .requestMatchers(HttpMethod.POST, "/api/v1/assistant/messages").permitAll()
                        // MCP endpoint (ADR 0010 §5): the same public, read-only tools; GET and DELETE answer 405
                        .requestMatchers("/mcp").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // OpenAPI / Swagger UI: public for development; to be protected or restricted under SD-702
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                // Tokens travel only in the Authorization header, never in cookies, and there is no
                // session, so a browser cannot attach credentials to a forged cross-site request.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${swiftdrop.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(IdentityClaims.ROLES);
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        return jwt -> {
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Token subject is missing", null));
            }

            UUID userId;
            try {
                userId = UUID.fromString(subject);
            } catch (IllegalArgumentException e) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Invalid user ID in token subject", null));
            }

            UserSummary user = userDirectory.findUser(userId)
                    .orElseThrow(() -> new OAuth2AuthenticationException(
                            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "User account not found", null)));

            if (!user.enabled()) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "User account is disabled", null));
            }

            Collection<GrantedAuthority> authorities = authoritiesConverter.convert(jwt);
            return new JwtAuthenticationToken(jwt, authorities, subject);
        };
    }
}
