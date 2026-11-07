package com.swiftdrop.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.swiftdrop.identity.api.IdentityClaims;
import com.swiftdrop.merchant.api.AuthenticatedMerchant;
import com.swiftdrop.merchant.api.MerchantAuthentication;

/**
 * Stateless bearer-token security for the REST API. The {@code JwtDecoder} comes from the identity
 * module. Roles become {@code ROLE_<name>} authorities from the token's roles claim, for URL rules
 * here or {@code @PreAuthorize} on endpoints.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

    /**
     * Merchant systems calling with an API key (ADR 0008 §5). Covers only the merchant order API and
     * has no JWT support, so bearer tokens are not accepted here; the API chain below does not read
     * {@code X-Api-Key}, so keys are not accepted anywhere else. Every failure is the same 401.
     * Ordered after Google sign-in, before the API chain.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    SecurityFilterChain merchantApiSecurity(HttpSecurity http, MerchantAuthentication merchants) throws Exception {
        http
                .securityMatcher("/api/v1/merchant-orders/**")
                .authorizeHttpRequests(requests -> requests.anyRequest().hasAuthority(AuthenticatedMerchant.AUTHORITY))
                .addFilterBefore(new MerchantApiKeyAuthenticationFilter(merchants), AnonymousAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(MerchantApiKeyAuthenticationFilter.UNAUTHORIZED)
                        .accessDeniedHandler((request, response, denied) ->
                                MerchantApiKeyAuthenticationFilter.UNAUTHORIZED.commence(request, response, null)))
                .csrf(AbstractHttpConfigurer::disable)
                // X-Api-Key is not an allowed CORS header: the merchant API is server-to-server only.
                .cors(Customizer.withDefaults())
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/v1/users", "/api/v1/auth/tokens", "/api/v1/auth/google/exchanges", "/api/v1/merchants").permitAll()
                        // Public tracking by tracking number (CUS-6); delivery returns no personal data there
                        .requestMatchers(HttpMethod.GET, "/api/v1/tracked-parcels/**").permitAll()
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

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(IdentityClaims.ROLES);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
