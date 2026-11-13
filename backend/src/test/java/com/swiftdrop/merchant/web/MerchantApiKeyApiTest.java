package com.swiftdrop.merchant.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.HubRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.identity.security.AccessTokenIssuer;
import com.swiftdrop.merchant.api.AuthenticatedMerchant;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantApiKey;
import com.swiftdrop.merchant.domain.MerchantApiKeyFormat;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.AuthorizationMatrixExecutor;
import com.swiftdrop.test.security.AuthorizationMatrixRow;
import com.swiftdrop.test.security.TestPrincipal;

/**
 * API-key management (SD-403) and API-key authentication (SD-404) through the real security chains,
 * with the real service and authenticator over mocked repositories.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
@MockitoBean(types = {ExternalIdentityRepository.class, GoogleLoginCodeRepository.class,
        ZoneRepository.class, PricingRuleRepository.class, ParcelRepository.class, ParcelStatusHistoryRepository.class,
        DeliveryProofRepository.class, OutboxEventRepository.class, ParcelAssignmentRepository.class,
        HubRepository.class, MerchantStatusHistoryRepository.class, MerchantWebhookEndpointRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventStateRepository.class,
        com.swiftdrop.merchant.domain.MerchantProcessedEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantPendingEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventAnomalyRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookAttemptRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookRedeliveryRepository.class,
        com.swiftdrop.notification.domain.NotificationRecordRepository.class})
class MerchantApiKeyApiTest {

    private static final String KEYS = "/api/v1/merchants/me/api-keys";
    private static final String ORDERS_PROBE = "/api/v1/merchant-orders/probe";

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final String PREFIX = "abcdefgh1234";
    private static final String VALID_KEY = "sdk_" + PREFIX + "_" + "S".repeat(43);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccessTokenIssuer tokens;

    @MockitoBean
    private UserRepository users;

    @MockitoBean
    private MerchantRepository merchants;

    @MockitoBean
    private MerchantApiKeyRepository apiKeys;

    private Merchant merchant;
    private MerchantApiKey storedKey;

    /** Stands in for the order API (SD-405) to observe the API-key chain. */
    @TestConfiguration
    static class ProbeConfiguration {

        @Bean
        ProbeController merchantOrdersProbe() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping(ORDERS_PROBE)
        Map<String, String> probe(@AuthenticationPrincipal AuthenticatedMerchant merchant) {
            return Map.of("merchantId", merchant.merchantId().toString(), "keyPrefix", merchant.keyPrefix());
        }
    }

    @BeforeEach
    void setUp() {
        merchant = new Merchant(USER_ID, "Shop", "Retail", "REG-1", "+94771234567", "Main St", null, null);
        ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
        merchant.approve(UUID.randomUUID());
        when(merchants.findByUserId(USER_ID)).thenReturn(Optional.of(merchant));
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));

        storedKey = new MerchantApiKey(MERCHANT_ID, PREFIX, MerchantApiKeyFormat.hash(VALID_KEY), "Live", USER_ID,
                Instant.parse("2026-11-08T09:00:00Z"));
        ReflectionTestUtils.setField(storedKey, "id", UUID.randomUUID());
        when(apiKeys.findByKeyPrefix(PREFIX)).thenReturn(Optional.of(storedKey));
        when(apiKeys.save(any(MerchantApiKey.class))).thenAnswer(invocation -> {
            MerchantApiKey key = invocation.getArgument(0);
            ReflectionTestUtils.setField(key, "id", UUID.randomUUID());
            return key;
        });
    }

    // SD-403: key management

    @Test
    @DisplayName("Creating a key returns the full key once, uncached, and stores only its hash")
    void create() throws Exception {
        MvcResult result = mvc.perform(post(KEYS).with(TestPrincipal.merchant(USER_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Production\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.label").value("Production"))
                .andExpect(jsonPath("$.keyPrefix").isString())
                .andExpect(jsonPath("$.keyHash").doesNotExist())
                .andReturn();

        String apiKey = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.apiKey");
        String prefix = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.keyPrefix");
        assertThat(MerchantApiKeyFormat.isWellFormed(apiKey)).isTrue();
        assertThat(MerchantApiKeyFormat.prefixOf(apiKey)).isEqualTo(prefix);
        verify(apiKeys).save(org.mockito.ArgumentMatchers.argThat(key ->
                key.getKeyHash().equals(MerchantApiKeyFormat.hash(apiKey)) && key.getMerchantId().equals(MERCHANT_ID)));
    }

    @Test
    @DisplayName("Listing keys never shows the hash or the secret")
    void list() throws Exception {
        when(apiKeys.findByMerchantIdOrderByCreatedAtDesc(MERCHANT_ID)).thenReturn(List.of(storedKey));

        String body = mvc.perform(get(KEYS).with(TestPrincipal.merchant(USER_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].keyPrefix").value(PREFIX))
                .andExpect(jsonPath("$[0].label").value("Live"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].keyHash").doesNotExist())
                .andExpect(jsonPath("$[0].apiKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(storedKey.getKeyHash());
    }

    @Test
    @DisplayName("Revoking an own key answers 204; another merchant's key answers 404")
    void revoke() throws Exception {
        when(apiKeys.findByIdAndMerchantId(storedKey.getId(), MERCHANT_ID)).thenReturn(Optional.of(storedKey));
        mvc.perform(delete(KEYS + "/" + storedKey.getId()).with(TestPrincipal.merchant(USER_ID).postProcessor()))
                .andExpect(status().isNoContent());
        assertThat(storedKey.isActive()).isFalse();

        mvc.perform(delete(KEYS + "/" + UUID.randomUUID()).with(TestPrincipal.merchant(USER_ID).postProcessor()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A sixth active key is refused with 409")
    void limit() throws Exception {
        when(apiKeys.countByMerchantIdAndRevokedAtIsNull(MERCHANT_ID)).thenReturn(5L);

        mvc.perform(post(KEYS).with(TestPrincipal.merchant(USER_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Sixth\"}"))
                .andExpect(status().isConflict());
        verify(apiKeys, never()).save(any());
    }

    @Test
    @DisplayName("A blank or over-long label is refused with 400")
    void invalidLabel() throws Exception {
        for (String body : List.of("{\"label\":\"  \"}", "{}", "{\"label\":\"" + "x".repeat(101) + "\"}")) {
            mvc.perform(post(KEYS).with(TestPrincipal.merchant(USER_ID).postProcessor())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("A merchant that is not approved cannot manage keys")
    void notApproved() throws Exception {
        Merchant pending = new Merchant(USER_ID, "Shop", "Retail", "REG-1", "+94771234567", "Main St", null, null);
        when(merchants.findByUserId(USER_ID)).thenReturn(Optional.of(pending));

        mvc.perform(get(KEYS).with(TestPrincipal.merchant(USER_ID).postProcessor()))
                .andExpect(status().isForbidden());
        mvc.perform(post(KEYS).with(TestPrincipal.merchant(USER_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Live\"}"))
                .andExpect(status().isForbidden());
        verify(apiKeys, never()).save(any());
    }

    @TestFactory
    @DisplayName("Authorization matrix for key management")
    Stream<DynamicTest> managementMatrix() {
        UUID otherUser = UUID.randomUUID();
        return AuthorizationMatrixExecutor.toDynamicTests(mvc, List.of(
                AuthorizationMatrixRow.row("GET keys: anonymous is 401", HttpMethod.GET, KEYS,
                        TestPrincipal.anonymous(), HttpStatus.UNAUTHORIZED),
                AuthorizationMatrixRow.row("GET keys: approved merchant is 200", HttpMethod.GET, KEYS,
                        TestPrincipal.merchant(USER_ID), HttpStatus.OK),
                AuthorizationMatrixRow.row("GET keys: customer is 403", HttpMethod.GET, KEYS,
                        TestPrincipal.customer(otherUser), HttpStatus.FORBIDDEN),
                AuthorizationMatrixRow.row("GET keys: courier is 403", HttpMethod.GET, KEYS,
                        TestPrincipal.courier(otherUser), HttpStatus.FORBIDDEN),
                AuthorizationMatrixRow.row("GET keys: dispatcher/admin is 403", HttpMethod.GET, KEYS,
                        TestPrincipal.dispatcherAdmin(otherUser), HttpStatus.FORBIDDEN),
                AuthorizationMatrixRow.row("GET keys: merchant role without a merchant profile is 403", HttpMethod.GET,
                        KEYS, TestPrincipal.merchant(otherUser), HttpStatus.FORBIDDEN),
                AuthorizationMatrixRow.row("GET keys: an API key is not accepted (401)", HttpMethod.GET, KEYS,
                        TestPrincipal.apiKey(VALID_KEY), HttpStatus.UNAUTHORIZED),
                AuthorizationMatrixRow.row("DELETE key: anonymous is 401", HttpMethod.DELETE,
                        KEYS + "/" + UUID.randomUUID(), TestPrincipal.anonymous(), HttpStatus.UNAUTHORIZED)));
    }

    // SD-404: API-key authentication on /api/v1/merchant-orders/**

    @Test
    @DisplayName("A valid key authenticates as its merchant on the merchant order API")
    void validKey() throws Exception {
        mvc.perform(get(ORDERS_PROBE).header("X-Api-Key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(MERCHANT_ID.toString()))
                .andExpect(jsonPath("$.keyPrefix").value(PREFIX));
    }

    @TestFactory
    @DisplayName("Every API-key failure gets the same 401 Problem Details response")
    Stream<DynamicTest> uniformFailures() {
        String wrongSecret = "sdk_" + PREFIX + "_" + "T".repeat(43);
        String unknownPrefix = "sdk_zzzzzzzzzzzz_" + "S".repeat(43);
        return Stream.of(
                DynamicTest.dynamicTest("missing header", () -> expectUniform401(null, null)),
                DynamicTest.dynamicTest("malformed key", () -> expectUniform401("not-a-key", null)),
                DynamicTest.dynamicTest("unknown prefix", () -> expectUniform401(unknownPrefix, null)),
                DynamicTest.dynamicTest("wrong secret", () -> expectUniform401(wrongSecret, null)),
                DynamicTest.dynamicTest("revoked key", () -> {
                    storedKey.revoke(Instant.now());
                    expectUniform401(VALID_KEY, null);
                }),
                DynamicTest.dynamicTest("suspended merchant", () -> {
                    merchant.suspend(UUID.randomUUID(), "Review");
                    expectUniform401(VALID_KEY, null);
                }),
                DynamicTest.dynamicTest("bearer token instead of a key", () ->
                        expectUniform401(null, TestPrincipal.merchant(USER_ID))));
    }

    @Test
    @DisplayName("A real, valid JWT works on user endpoints but is not accepted on the merchant order API")
    void realBearerTokenRejectedOnOrderApi() throws Exception {
        User user = new User("merchant@example.com", "hash", "Merchant", Set.of(Role.MERCHANT));
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        String token = tokens.issue(user).value();

        mvc.perform(get(KEYS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get(ORDERS_PROBE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "ApiKey"));
    }

    @Test
    @DisplayName("A browser preflight cannot send X-Api-Key cross-origin")
    void corsRejectsApiKeyHeader() throws Exception {
        mvc.perform(options(ORDERS_PROBE)
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Api-Key"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private void expectUniform401(String apiKey, TestPrincipal bearer) throws Exception {
        var request = get(ORDERS_PROBE);
        if (apiKey != null) {
            request.header("X-Api-Key", apiKey);
        }
        if (bearer != null) {
            request.with(bearer.postProcessor());
        }
        mvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "ApiKey"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("A valid X-Api-Key header is required"));
    }
}
