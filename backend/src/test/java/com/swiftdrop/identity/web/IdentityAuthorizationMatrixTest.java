package com.swiftdrop.identity.web;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.identity.application.GoogleExchangeService;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCode;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.test.security.AuthorizationMatrixExecutor;
import com.swiftdrop.test.security.AuthorizationMatrixRow;
import com.swiftdrop.test.security.TestPrincipal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Baseline authorization matrix tests (TST-2 / SD-106) for the identity module endpoints.
 * Verifies anonymous access rules, authenticated JWT token checks, and expected error responses.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class IdentityAuthorizationMatrixTest {

    private static final UUID EXISTING_CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EXISTING_MERCHANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EXISTING_COURIER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID EXISTING_ADMIN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID NON_EXISTENT_USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private GoogleExchangeService exchangeService;

    @MockitoBean
    private UserRepository users;

    @MockitoBean
    private ExternalIdentityRepository identities;

    @MockitoBean
    private GoogleLoginCodeRepository codeRepository;

    private final Map<String, GoogleLoginCode> storedCodes = new HashMap<>();
    private String validExchangeCode;

    @BeforeEach
    void setUp() {
        storedCodes.clear();

        // Code repository mock matching GoogleExchangeApiTest
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

        // Setup users
        User customer = withId(new User("customer@example.com", passwordEncoder.encode("correct-password"), "Customer Alice", Set.of(Role.CUSTOMER)), EXISTING_CUSTOMER_ID);
        User merchant = withId(new User("merchant@example.com", passwordEncoder.encode("correct-password"), "Merchant Shop", Set.of(Role.MERCHANT)), EXISTING_MERCHANT_ID);
        User courier = withId(new User("courier@example.com", passwordEncoder.encode("correct-password"), "Courier Bob", Set.of(Role.COURIER)), EXISTING_COURIER_ID);
        User admin = withId(new User("admin@example.com", passwordEncoder.encode("correct-password"), "Admin Dave", Set.of(Role.DISPATCHER_ADMIN)), EXISTING_ADMIN_ID);

        when(users.findById(EXISTING_CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(users.findById(EXISTING_MERCHANT_ID)).thenReturn(Optional.of(merchant));
        when(users.findById(EXISTING_COURIER_ID)).thenReturn(Optional.of(courier));
        when(users.findById(EXISTING_ADMIN_ID)).thenReturn(Optional.of(admin));
        when(users.findById(NON_EXISTENT_USER_ID)).thenReturn(Optional.empty());

        when(users.findByEmailIgnoringCase(any())).thenCallRealMethod();
        when(users.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(users.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        when(users.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));

        // Create a valid exchange code
        validExchangeCode = exchangeService.createExchangeCode(EXISTING_CUSTOMER_ID);
    }

    @TestFactory
    Stream<DynamicTest> identityAuthorizationMatrix() {
        return AuthorizationMatrixExecutor.toDynamicTests(mvc, identityMatrixRows());
    }

    @Test
    void executeAllIdentityMatrixRows() {
        AuthorizationMatrixExecutor.executeAll(mvc, identityMatrixRows());
    }

    private List<AuthorizationMatrixRow> identityMatrixRows() {
        return List.of(
                // POST /api/v1/users (Registration)
                AuthorizationMatrixRow.row(
                        "POST /api/v1/users: Anonymous valid registration returns 201 Created",
                        HttpMethod.POST,
                        "/api/v1/users",
                        TestPrincipal.anonymous(),
                        HttpStatus.CREATED,
                        req -> req.contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fullName\": \"New User\", \"email\": \"new@example.com\", \"password\": \"secure-pass-123\"}")
                ),
                AuthorizationMatrixRow.row(
                        "POST /api/v1/users: Invalid registration payload returns 400 Bad Request",
                        HttpMethod.POST,
                        "/api/v1/users",
                        TestPrincipal.anonymous(),
                        HttpStatus.BAD_REQUEST,
                        req -> req.contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fullName\": \" \", \"email\": \"invalid-email\", \"password\": \"short\"}")
                ),

                // POST /api/v1/auth/tokens (Login)
                AuthorizationMatrixRow.row(
                        "POST /api/v1/auth/tokens: Anonymous valid credentials return 200 OK",
                        HttpMethod.POST,
                        "/api/v1/auth/tokens",
                        TestPrincipal.anonymous(),
                        HttpStatus.OK,
                        req -> req.contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \"customer@example.com\", \"password\": \"correct-password\"}")
                ),
                AuthorizationMatrixRow.row(
                        "POST /api/v1/auth/tokens: Invalid credentials return 401 Unauthorized",
                        HttpMethod.POST,
                        "/api/v1/auth/tokens",
                        TestPrincipal.anonymous(),
                        HttpStatus.UNAUTHORIZED,
                        req -> req.contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \"customer@example.com\", \"password\": \"wrong-password\"}")
                ),
                AuthorizationMatrixRow.row(
                        "POST /api/v1/auth/tokens: Blank credentials return 400 Bad Request",
                        HttpMethod.POST,
                        "/api/v1/auth/tokens",
                        TestPrincipal.anonymous(),
                        HttpStatus.BAD_REQUEST,
                        req -> req.contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \" \", \"password\": \"\"}")
                ),

                // POST /api/v1/auth/google/exchanges (Google Code Exchange)
                AuthorizationMatrixRow.row(
                        "POST /api/v1/auth/google/exchanges: Valid unredeemed code returns 200 OK",
                        HttpMethod.POST,
                        "/api/v1/auth/google/exchanges",
                        TestPrincipal.anonymous(),
                        HttpStatus.OK,
                        req -> req.contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\": \"%s\"}".formatted(validExchangeCode))
                ),
                AuthorizationMatrixRow.row(
                        "POST /api/v1/auth/google/exchanges: Invalid exchange code returns 401 Unauthorized",
                        HttpMethod.POST,
                        "/api/v1/auth/google/exchanges",
                        TestPrincipal.anonymous(),
                        HttpStatus.UNAUTHORIZED,
                        req -> req.contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\": \"invalid-or-unknown-code\"}")
                ),
                AuthorizationMatrixRow.row(
                        "POST /api/v1/auth/google/exchanges: Blank code returns 400 Bad Request",
                        HttpMethod.POST,
                        "/api/v1/auth/google/exchanges",
                        TestPrincipal.anonymous(),
                        HttpStatus.BAD_REQUEST,
                        req -> req.contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\": \"   \"}")
                ),

                // GET /api/v1/users/me (Current User Profile)
                AuthorizationMatrixRow.row(
                        "GET /api/v1/users/me: Anonymous request returns 401 Unauthorized",
                        HttpMethod.GET,
                        "/api/v1/users/me",
                        TestPrincipal.anonymous(),
                        HttpStatus.UNAUTHORIZED
                ),
                AuthorizationMatrixRow.row(
                        "GET /api/v1/users/me: Authenticated CUSTOMER returns 200 OK",
                        HttpMethod.GET,
                        "/api/v1/users/me",
                        TestPrincipal.customer(EXISTING_CUSTOMER_ID),
                        HttpStatus.OK
                ),
                AuthorizationMatrixRow.row(
                        "GET /api/v1/users/me: Authenticated MERCHANT returns 200 OK",
                        HttpMethod.GET,
                        "/api/v1/users/me",
                        TestPrincipal.merchant(EXISTING_MERCHANT_ID),
                        HttpStatus.OK
                ),
                AuthorizationMatrixRow.row(
                        "GET /api/v1/users/me: Authenticated COURIER returns 200 OK",
                        HttpMethod.GET,
                        "/api/v1/users/me",
                        TestPrincipal.courier(EXISTING_COURIER_ID),
                        HttpStatus.OK
                ),
                AuthorizationMatrixRow.row(
                        "GET /api/v1/users/me: Authenticated DISPATCHER_ADMIN returns 200 OK",
                        HttpMethod.GET,
                        "/api/v1/users/me",
                        TestPrincipal.dispatcherAdmin(EXISTING_ADMIN_ID),
                        HttpStatus.OK
                ),
                AuthorizationMatrixRow.row(
                        "GET /api/v1/users/me: Authenticated non-existent user returns 404 Not Found",
                        HttpMethod.GET,
                        "/api/v1/users/me",
                        TestPrincipal.customer(NON_EXISTENT_USER_ID),
                        HttpStatus.NOT_FOUND
                )
        );
    }

    private static User withId(User user, UUID id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
