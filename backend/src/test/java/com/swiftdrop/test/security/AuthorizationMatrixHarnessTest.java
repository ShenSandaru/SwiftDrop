package com.swiftdrop.test.security;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Self-test for the authorization matrix harness (TST-2) using an isolated fixture controller.
 * Verifies role mapping, custom subject UUIDs, anonymous rejections, 403 wrong-role responses,
 * ownership simulation, and API key header simulation.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
@Import({AuthorizationMatrixHarnessTest.HarnessFixtureController.class,
        AuthorizationMatrixHarnessTest.TestSecurityConfig.class})
class AuthorizationMatrixHarnessTest {

    private static final UUID ALICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private ExternalIdentityRepository identities;
    @MockitoBean
    private GoogleLoginCodeRepository googleCodes;

    @TestFactory
    Stream<DynamicTest> authorizationMatrixDynamicTests() {
        return AuthorizationMatrixExecutor.toDynamicTests(mvc, matrixRows());
    }

    @Test
    void batchExecutionRunsAllMatrixRows() {
        AuthorizationMatrixExecutor.executeAll(mvc, matrixRows());
    }

    @Test
    void executorProducesDetailedFailureMessageOnStatusMismatch() {
        AuthorizationMatrixRow failingRow = AuthorizationMatrixRow.row(
                "Deliberate failure test",
                HttpMethod.GET,
                "/test/harness/customer-only",
                TestPrincipal.anonymous(),
                HttpStatus.OK // Actually returns 401
        );

        assertThatThrownBy(() -> AuthorizationMatrixExecutor.execute(mvc, failingRow))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Authorization matrix failure: Deliberate failure test")
                .hasMessageContaining("Expected HTTP status: 200 (OK)")
                .hasMessageContaining("Actual HTTP status: 401");
    }

    private List<AuthorizationMatrixRow> matrixRows() {
        return List.of(
                // Public endpoint
                AuthorizationMatrixRow.row("Anonymous access to public endpoint",
                        HttpMethod.GET, "/test/harness/public", TestPrincipal.anonymous(), HttpStatus.OK),
                AuthorizationMatrixRow.row("Customer access to public endpoint",
                        HttpMethod.GET, "/test/harness/public", TestPrincipal.customer(), HttpStatus.OK),

                // Customer-only endpoint
                AuthorizationMatrixRow.row("Anonymous access to customer endpoint rejected",
                        HttpMethod.GET, "/test/harness/customer-only", TestPrincipal.anonymous(), HttpStatus.UNAUTHORIZED),
                AuthorizationMatrixRow.row("Customer access to customer endpoint permitted",
                        HttpMethod.GET, "/test/harness/customer-only", TestPrincipal.customer(), HttpStatus.OK),
                AuthorizationMatrixRow.row("Courier access to customer endpoint forbidden",
                        HttpMethod.GET, "/test/harness/customer-only", TestPrincipal.courier(), HttpStatus.FORBIDDEN),
                AuthorizationMatrixRow.row("Merchant access to customer endpoint forbidden",
                        HttpMethod.GET, "/test/harness/customer-only", TestPrincipal.merchant(), HttpStatus.FORBIDDEN),
                AuthorizationMatrixRow.row("Admin access to customer endpoint forbidden",
                        HttpMethod.GET, "/test/harness/customer-only", TestPrincipal.dispatcherAdmin(), HttpStatus.FORBIDDEN),

                // Courier-only endpoint
                AuthorizationMatrixRow.row("Courier access permitted",
                        HttpMethod.GET, "/test/harness/courier-only", TestPrincipal.courier(), HttpStatus.OK),
                AuthorizationMatrixRow.row("Customer access to courier endpoint forbidden",
                        HttpMethod.GET, "/test/harness/courier-only", TestPrincipal.customer(), HttpStatus.FORBIDDEN),

                // Merchant-only endpoint
                AuthorizationMatrixRow.row("Merchant access permitted",
                        HttpMethod.GET, "/test/harness/merchant-only", TestPrincipal.merchant(), HttpStatus.OK),
                AuthorizationMatrixRow.row("Customer access to merchant endpoint forbidden",
                        HttpMethod.GET, "/test/harness/merchant-only", TestPrincipal.customer(), HttpStatus.FORBIDDEN),

                // Dispatcher/Admin endpoint
                AuthorizationMatrixRow.row("Dispatcher/Admin access permitted",
                        HttpMethod.GET, "/test/harness/admin-only", TestPrincipal.dispatcherAdmin(), HttpStatus.OK),
                AuthorizationMatrixRow.row("Courier access to admin endpoint forbidden",
                        HttpMethod.GET, "/test/harness/admin-only", TestPrincipal.courier(), HttpStatus.FORBIDDEN),

                // Ownership endpoint
                AuthorizationMatrixRow.row("Owner can access their own resource",
                        HttpMethod.GET, "/test/harness/ownership/" + ALICE_ID, TestPrincipal.customer(ALICE_ID), HttpStatus.OK),
                AuthorizationMatrixRow.row("Non-owner is forbidden from accessing another user's resource",
                        HttpMethod.GET, "/test/harness/ownership/" + ALICE_ID, TestPrincipal.customer(BOB_ID), HttpStatus.FORBIDDEN),

                // API Key header simulation
                AuthorizationMatrixRow.row("Valid API key header accepted",
                        HttpMethod.POST, "/test/harness/api-key", TestPrincipal.apiKey("valid-key-123"), HttpStatus.OK),
                AuthorizationMatrixRow.row("Missing API key header rejected",
                        HttpMethod.POST, "/test/harness/api-key", TestPrincipal.anonymous(), HttpStatus.UNAUTHORIZED)
        );
    }

    @RestController
    @RequestMapping("/test/harness")
    static class HarnessFixtureController {

        @GetMapping("/public")
        String publicEndpoint() {
            return "public-ok";
        }

        @GetMapping("/customer-only")
        @PreAuthorize("hasRole('CUSTOMER')")
        String customerOnly() {
            return "customer-ok";
        }

        @GetMapping("/courier-only")
        @PreAuthorize("hasRole('COURIER')")
        String courierOnly() {
            return "courier-ok";
        }

        @GetMapping("/merchant-only")
        @PreAuthorize("hasRole('MERCHANT')")
        String merchantOnly() {
            return "merchant-ok";
        }

        @GetMapping("/admin-only")
        @PreAuthorize("hasRole('DISPATCHER_ADMIN')")
        String adminOnly() {
            return "admin-ok";
        }

        @GetMapping("/ownership/{ownerId}")
        String ownershipCheck(@PathVariable UUID ownerId, @AuthenticationPrincipal Jwt jwt) {
            if (jwt == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }
            if (!ownerId.toString().equalsIgnoreCase(jwt.getSubject())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the resource owner");
            }
            return "owner-ok";
        }

        @PostMapping("/api-key")
        String apiKeyCheck(@RequestHeader(value = "X-API-Key", required = false) String apiKey) {
            if (apiKey == null || !apiKey.equals("valid-key-123")) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid API key");
            }
            return "api-key-ok";
        }
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        @Order(0)
        SecurityFilterChain harnessSecurity(HttpSecurity http) throws Exception {
            http
                    .securityMatcher("/test/harness/**")
                    .authorizeHttpRequests(requests -> requests
                            .requestMatchers("/test/harness/public", "/test/harness/api-key").permitAll()
                            .anyRequest().authenticated())
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
            return http.build();
        }
    }
}
