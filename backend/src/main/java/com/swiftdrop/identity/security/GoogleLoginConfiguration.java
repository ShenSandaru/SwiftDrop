package com.swiftdrop.identity.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.util.StringUtils;

/**
 * Google sign-in with OpenID Connect, active only when {@code SWIFTDROP_GOOGLE_CLIENT_ID} is set.
 * Without it neither the client registration nor these endpoints exist, and local login is
 * unaffected.
 *
 * <p>{@code GET /oauth2/authorization/google} redirects to Google; Google redirects back to
 * {@code /login/oauth2/code/google}. Spring Security checks the state, exchanges the code and
 * validates the ID token (signature from Google's keys, issuer, audience, expiry, nonce). An HTTP
 * session holds only the pending authorization request and is invalidated when the callback
 * completes; the result is a SwiftDrop access token, not a session.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(GoogleLoginConfiguration.ClientIdPresent.class)
@EnableConfigurationProperties(GoogleLoginProperties.class)
class GoogleLoginConfiguration {

    static final String REGISTRATION_ID = "google";

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(GoogleLoginProperties properties) {
        // Google's endpoints, issuer and the scopes openid, profile and email.
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder(REGISTRATION_ID)
                .clientId(properties.clientId())
                .clientSecret(properties.clientSecret())
                .build());
    }

    /** Ordered before the API chain in {@code config}, which handles every other request. */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain googleLoginSecurity(HttpSecurity http, GoogleLoginResponses responses) throws Exception {
        http
                .securityMatcher("/oauth2/authorization/**", "/login/oauth2/code/**")
                .authorizeHttpRequests(requests -> requests.anyRequest().denyAll())
                // The authentication is never stored in the session; the response carries the token.
                .securityContext(context -> context
                        .securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .requestCache(cache -> cache.disable())
                .oauth2Login(login -> login
                        // Google's access token is not kept beyond the request: the session is
                        // invalidated by the handlers, and nothing calls Google APIs later.
                        .authorizedClientRepository(new HttpSessionOAuth2AuthorizedClientRepository())
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService()))
                        .successHandler(responses)
                        .failureHandler(responses));
        return http.build();
    }

    /** Uses only the validated ID token claims; Google's ID token carries email and name. */
    private static OidcUserService oidcUserService() {
        OidcUserService service = new OidcUserService();
        service.setRetrieveUserInfo(request -> false);
        return service;
    }

    static class ClientIdPresent implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty(GoogleLoginProperties.CLIENT_ID_PROPERTY));
        }
    }
}
