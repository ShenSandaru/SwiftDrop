package com.swiftdrop.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;

/**
 * The HTTP behavior of registration, login, bearer-token validation and role checks, through the
 * real security filter chain. The repository is mocked, so this does not touch PostgreSQL.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789"})
@AutoConfigureMockMvc
@Import(AuthenticationApiTest.RoleFixtureController.class)
class AuthenticationApiTest {

    private static final String DISPATCHER_ONLY = "/test-fixture/dispatcher-only";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtEncoder encoder;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private UserRepository users;

    @BeforeEach
    void setUp() {
        when(users.findByEmailIgnoringCase(any())).thenCallRealMethod();
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(users.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0)));
    }

    // Registration

    @Test
    void registersCustomerWithoutExposingThePassword() throws Exception {
        register("""
                {"fullName": "Alice", "email": " Alice@Example.COM ", "password": "correct horse"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.roles", contains("CUSTOMER")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = savedUser();
        assertThat(saved.getPasswordHash()).isNotEqualTo("correct horse").startsWith("{bcrypt}");
    }

    @Test
    void publicRegistrationCannotGrantPrivilegedRoles() throws Exception {
        for (String role : List.of("MERCHANT", "COURIER", "DISPATCHER_ADMIN")) {
            register("""
                    {"fullName": "Mallory", "email": "mallory@example.com", "password": "correct horse",
                     "roles": ["%s"], "role": "%s"}""".formatted(role, role))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.roles", contains("CUSTOMER")));
        }
    }

    @Test
    void rejectsInvalidRegistrationWithoutEchoingValues() throws Exception {
        register("""
                {"fullName": " ", "email": "not-an-email", "password": "short"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("email", "fullName", "password")))
                .andExpect(content -> assertThat(content.getResponse().getContentAsString())
                        .doesNotContain("not-an-email", "short"));
        register("{}").andExpect(status().isBadRequest());
        register("{\"fullName\": \"A\", \"email\": \"a@example.com\", \"password\": \"" + "x".repeat(73) + "\"}")
                .andExpect(status().isBadRequest());
        register("not json").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(user("x", Role.CUSTOMER)));

        register("""
                {"fullName": "Alice", "email": "ALICE@example.com", "password": "correct horse"}""")
                .andExpect(status().isConflict());
    }

    // Login and tokens

    @Test
    void loginIssuesTokenThatAuthenticatesTheUser() throws Exception {
        User alice = withId(user(passwordEncoder.encode("correct horse"), Role.CUSTOMER));
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(users.findById(alice.getId())).thenReturn(Optional.of(alice));

        String body = login("ALICE@example.com", "correct horse")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alice.getId().toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void invalidCredentialsGiveTheSameResponseWhetherOrNotTheAccountExists() throws Exception {
        User alice = withId(user(passwordEncoder.encode("correct horse"), Role.CUSTOMER));
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        String wrongPassword = login("alice@example.com", "wrong password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownAccount = login("nobody@example.com", "correct horse")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownAccount).contains("Invalid email or password");
    }

    @Test
    void protectedEndpointsRejectMissingMalformedForgedExpiredAndForeignTokens() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get(DISPATCHER_ONLY)).andExpect(status().isUnauthorized());

        Instant now = Instant.now();
        String forged = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(
                        "another-key-that-is-long-enough-for-hs256!!".getBytes(), "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims(now, now.plusSeconds(600), "swiftdrop", Role.DISPATCHER_ADMIN)))
                .getTokenValue();
        String validButTampered = token(claims(now, now.plusSeconds(600), "swiftdrop", Role.CUSTOMER));

        for (String bad : List.of(
                "not-a-jwt",
                "a.b.c",
                forged,
                tamperSignature(validButTampered),
                // Expired beyond the 60-second clock skew
                token(claims(now.minusSeconds(900), now.minusSeconds(120), "swiftdrop", Role.DISPATCHER_ADMIN)),
                token(claims(now, now.plusSeconds(600), "someone-else", Role.DISPATCHER_ADMIN)))) {
            mvc.perform(get(DISPATCHER_ONLY).header(HttpHeaders.AUTHORIZATION, "Bearer " + bad))
                    .andExpect(status().isUnauthorized());
        }
    }

    // Roles

    @Test
    void roleProtectedEndpointPermitsOnlyTheRequiredRole() throws Exception {
        Instant now = Instant.now();
        for (Role role : Role.values()) {
            String token = token(claims(now, now.plusSeconds(600), "swiftdrop", role));
            mvc.perform(get(DISPATCHER_ONLY).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(role == Role.DISPATCHER_ADMIN ? status().isOk() : status().isForbidden());
        }
    }

    @Test
    void healthIsPublicAndCorsAllowsTheConfiguredFrontendOnly() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().is(not(401)));

        mvc.perform(options("/api/v1/users")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
        mvc.perform(options("/api/v1/users")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    private ResultActions register(String json) throws Exception {
        return mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/tokens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private String token(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** Changes the first signature character, which is always significant in base64url. */
    private static String tamperSignature(String token) {
        int start = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(start) == 'A' ? 'B' : 'A';
        return token.substring(0, start) + replacement + token.substring(start + 1);
    }

    private static JwtClaimsSet claims(Instant issuedAt, Instant expiresAt, String issuer, Role role) {
        return JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of(role.name()))
                .build();
    }

    private static User user(String passwordHash, Role role) {
        return new User("alice@example.com", passwordHash, "Alice", Set.of(role));
    }

    private static User withId(User user) {
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    /** Test-only endpoint guarded by a role, standing in for future role-restricted endpoints. */
    @RestController
    static class RoleFixtureController {

        @GetMapping(DISPATCHER_ONLY)
        @PreAuthorize("hasRole('DISPATCHER_ADMIN')")
        String dispatcherOnly() {
            return "ok";
        }
    }
}
