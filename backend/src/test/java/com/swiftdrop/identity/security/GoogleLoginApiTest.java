package com.swiftdrop.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.identity.domain.ExternalIdentity;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCode;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.IdentityProvider;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;

/**
 * The Google OIDC login through the real filter chain, without Google: the redirect, the callback
 * with Spring Security's state, nonce and ID token validation, exchange code issuance, and account resolution.
 * All claim validation is Spring Security's default.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=" + GoogleLoginApiTest.CLIENT_ID,
        "swiftdrop.security.google.client-secret=" + GoogleLoginApiTest.CLIENT_SECRET,
        "swiftdrop.frontend.base-url=http://localhost:3000"})
@AutoConfigureMockMvc
class GoogleLoginApiTest {

    static final String CLIENT_ID = "test-client.apps.googleusercontent.com";
    static final String CLIENT_SECRET = "test-only-google-client-secret-0123456789";
    private static final String ISSUER = "https://accounts.google.com";
    private static final String SUBJECT = "110169484474386276334";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private ExternalIdentityRepository identities;
    @MockitoBean
    private GoogleLoginCodeRepository codeRepository;
    @MockitoBean
    private OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokenEndpoint;

    private final Map<String, GoogleLoginCode> storedCodes = new HashMap<>();

    @BeforeEach
    void setUp() {
        storedCodes.clear();
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(users.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0)));
        when(codeRepository.save(any(GoogleLoginCode.class))).thenAnswer(call -> {
            GoogleLoginCode code = call.getArgument(0);
            storedCodes.put(code.getCodeHash(), code);
            return code;
        });
        when(codeRepository.markUsedIfValid(any(), any())).thenAnswer(call -> {
            String hash = call.getArgument(0);
            Instant now = call.getArgument(1);
            GoogleLoginCode code = storedCodes.get(hash);
            if (code != null && code.getUsedAt() == null && code.getExpiresAt().isAfter(now)) {
                ReflectionTestUtils.setField(code, "usedAt", now);
                return 1;
            }
            return 0;
        });
        when(codeRepository.findById(any())).thenAnswer(call -> {
            String hash = call.getArgument(0);
            return Optional.ofNullable(storedCodes.get(hash));
        });
    }

    @Test
    void redirectsToGoogleWithStateNonceAndOpenIdScope() throws Exception {
        mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("https://accounts.google.com/")))
                .andExpect(result -> {
                    Map<String, String> query = query(result);
                    assertThat(query).containsKeys("state", "nonce")
                            .containsEntry("client_id", CLIENT_ID)
                            .containsEntry("response_type", "code");
                    assertThat(query.get("scope")).contains("openid", "email");
                });
    }

    @Test
    void firstSignInCreatesCustomerAndRedirectsWithExchangeCode() throws Exception {
        MvcResult callbackResult = callback(claims -> { })
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("http://localhost:3000/auth/google/callback?code=")))
                .andReturn();
        String location = callbackResult.getResponse().getHeader(HttpHeaders.LOCATION);
        String code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
        assertThat(code).isNotNull().hasSizeGreaterThanOrEqualTo(32);

        User created = savedUser();
        assertThat(created.getRoles()).containsExactly(Role.CUSTOMER);
        assertThat(created.getPasswordHash()).isNull();
        verify(identities).saveAndFlush(any(ExternalIdentity.class));

        when(users.findById(created.getId())).thenReturn(Optional.of(created));

        String exchangeBody = mvc.perform(post("/api/v1/auth/google/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(exchangeBody, "$.accessToken");

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.roles", contains("CUSTOMER")));
    }

    @Test
    void repeatedSignInWithTheSameSubjectResolvesToTheSameAccount() throws Exception {
        User alice = withId(new User("alice@example.com", null, "Alice", Set.of(Role.CUSTOMER)));
        when(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, SUBJECT))
                .thenReturn(Optional.of(new ExternalIdentity(IdentityProvider.GOOGLE, SUBJECT, alice.getId())));
        when(users.findById(alice.getId())).thenReturn(Optional.of(alice));

        for (String email : List.of("alice@example.com", "alice.new@example.com")) {
            MvcResult callbackResult = callback(claims -> claims.claim("email", email))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string(HttpHeaders.LOCATION, startsWith("http://localhost:3000/auth/google/callback?code=")))
                    .andReturn();
            String code = UriComponentsBuilder.fromUriString(callbackResult.getResponse().getHeader(HttpHeaders.LOCATION))
                    .build().getQueryParams().getFirst("code");

            String exchangeBody = mvc.perform(post("/api/v1/auth/google/exchanges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\": \"%s\"}".formatted(code)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            mvc.perform(get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(exchangeBody, "$.accessToken")))
                    .andExpect(jsonPath("$.id").value(alice.getId().toString()));
        }
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void matchingEmailWithoutLinkIsRejectedWithConflictEmailExistsErrorRedirect() throws Exception {
        when(users.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(withId(new User("alice@example.com", "{bcrypt}x", "Alice", Set.of(Role.CUSTOMER)))));

        callback(claims -> { })
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://localhost:3000/auth/google/callback?error=conflict_email_exists"));
        verify(users, never()).saveAndFlush(any());
        verify(identities, never()).saveAndFlush(any());
    }

    @Test
    void unverifiedOrMissingEmailVerificationRedirectsWithUnverifiedEmailError() throws Exception {
        List<Consumer<JwtClaimsSet.Builder>> unverified = List.of(
                claims -> claims.claim("email_verified", false),
                claims -> claims.claims(c -> c.remove("email_verified")),
                claims -> claims.claim("email_verified", "yes"));
        for (Consumer<JwtClaimsSet.Builder> change : unverified) {
            callback(change)
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(header().string(HttpHeaders.LOCATION,
                            "http://localhost:3000/auth/google/callback?error=unverified_email"));
        }
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void privilegedRoleClaimsFromGoogleAreIgnored() throws Exception {
        callback(claims -> claims.claim("roles", List.of("DISPATCHER_ADMIN", "MERCHANT", "COURIER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("http://localhost:3000/auth/google/callback?code=")));

        assertThat(savedUser().getRoles()).containsExactly(Role.CUSTOMER);
    }

    @Test
    void rejectsIdTokensThatFailValidationWithOAuthFailedErrorRedirect() throws Exception {
        Instant now = Instant.now();
        List<Consumer<JwtClaimsSet.Builder>> invalid = List.of(
                claims -> claims.issuer("https://evil.example"),
                claims -> claims.audience(List.of("another-client")),
                claims -> claims.issuedAt(now.minusSeconds(900)).expiresAt(now.minusSeconds(300)),
                claims -> claims.claim("nonce", "not-the-requested-nonce"),
                claims -> claims.claims(c -> c.remove("nonce")),
                claims -> claims.claims(c -> c.remove("sub")));
        for (Consumer<JwtClaimsSet.Builder> change : invalid) {
            callback(change)
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(header().string(HttpHeaders.LOCATION,
                            "http://localhost:3000/auth/google/callback?error=oauth_failed"));
        }
        verifyNoInteractions(users, identities);
    }

    @Test
    void rejectsIdTokenWithForgedSignatureWithOAuthFailedErrorRedirect() throws Exception {
        callback(claims -> { }, "another-secret-that-is-long-enough-for-hs256")
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://localhost:3000/auth/google/callback?error=oauth_failed"));
        verifyNoInteractions(users, identities);
    }

    @Test
    void rejectsCallbackWithWrongOrMissingStateWithOAuthFailedErrorRedirect() throws Exception {
        MvcResult authorization = mvc.perform(get("/oauth2/authorization/google")).andReturn();
        MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession();

        mvc.perform(get("/login/oauth2/code/google").session(session)
                        .param("code", "code").param("state", "forged-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://localhost:3000/auth/google/callback?error=oauth_failed"));

        mvc.perform(get("/login/oauth2/code/google").param("code", "code").param("state", "any"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://localhost:3000/auth/google/callback?error=oauth_failed"));

        verifyNoInteractions(tokenEndpoint, users, identities);
    }

    @Test
    void cancelledCallbackRedirectsWithCancelledError() throws Exception {
        MvcResult authorization = mvc.perform(get("/oauth2/authorization/google")).andReturn();
        MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession();
        Map<String, String> query = query(authorization);

        mvc.perform(get("/login/oauth2/code/google").session(session)
                        .param("error", "access_denied")
                        .param("state", query.get("state")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://localhost:3000/auth/google/callback?error=cancelled"));

        verifyNoInteractions(tokenEndpoint, users, identities);
    }

    private ResultActions callback(Consumer<JwtClaimsSet.Builder> change) throws Exception {
        return callback(change, CLIENT_SECRET);
    }

    /** Starts the login, then completes the callback with an ID token built from the claims. */
    private ResultActions callback(Consumer<JwtClaimsSet.Builder> change, String signingSecret) throws Exception {
        MvcResult authorization = mvc.perform(get("/oauth2/authorization/google")).andReturn();
        Map<String, String> query = query(authorization);
        MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession();

        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(SUBJECT)
                .audience(List.of(CLIENT_ID))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                // The redirect carries the nonce hash, which is what the ID token must contain.
                .claim("nonce", query.get("nonce"))
                .claim("email", "alice@example.com")
                .claim("email_verified", true)
                .claim("name", "Alice");
        change.accept(claims);
        String idToken = sign(claims.build(), signingSecret);

        when(tokenEndpoint.getTokenResponse(any())).thenReturn(OAuth2AccessTokenResponse
                .withToken("google-access-token")
                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                .expiresIn(3600)
                .scopes(Set.of("openid", "email", "profile"))
                .additionalParameters(Map.of(OidcParameterNames.ID_TOKEN, idToken))
                .build());

        return mvc.perform(get("/login/oauth2/code/google").session(session)
                .param("code", "authorization-code")
                .param("state", query.get("state")));
    }

    private static String sign(JwtClaimsSet claims, String secret) {
        return NimbusJwtEncoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static Map<String, String> query(MvcResult redirect) {
        String location = redirect.getResponse().getHeader(HttpHeaders.LOCATION);
        Map<String, String> result = new HashMap<>();
        UriComponentsBuilder.fromUriString(location).build(true).getQueryParams()
                .forEach((name, values) -> result.put(name,
                        URLDecoder.decode(values.getFirst(), StandardCharsets.UTF_8)));
        return result;
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static User withId(User user) {
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @TestConfiguration
    static class HmacIdTokens {

        /** Spring Security's ID token decoder and validators, with the client secret as the key. */
        @Bean
        JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
            OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
            factory.setJwsAlgorithmResolver(registration -> MacAlgorithm.HS256);
            return factory;
        }
    }
}
