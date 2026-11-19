package com.swiftdrop.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantApiKey;
import com.swiftdrop.merchant.domain.MerchantApiKeyFormat;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.pricing.domain.PricingRule;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/**
 * {@code pricingAndZonesSecurity} (ADR 0008 addendum): {@code POST /api/v1/price-quotes} and
 * {@code GET /api/v1/zones} accept either a bearer token or a merchant API key, never both, and
 * every existing chain's behavior (merchant-order API, other JWT-protected endpoints) stays
 * unaffected. The repositories are mocked, so this runs the real filter chain without PostgreSQL.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
@MockitoBean(types = {
        com.swiftdrop.pricing.domain.DistrictRepository.class,
        com.swiftdrop.pricing.domain.PricingConfigRepository.class,
        com.swiftdrop.identity.domain.UserAddressRepository.class,
        ParcelAssignmentRepository.class,
        com.swiftdrop.delivery.domain.HubRepository.class,
        com.swiftdrop.delivery.domain.HubLinkRepository.class,
        com.swiftdrop.delivery.domain.HubStaffRepository.class,
        com.swiftdrop.notification.domain.NotificationPendingEventRepository.class,
        com.swiftdrop.notification.domain.NotificationConsumerStateRepository.class,
        MerchantStatusHistoryRepository.class,
        MerchantWebhookEndpointRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventStateRepository.class,
        com.swiftdrop.merchant.domain.MerchantProcessedEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantPendingEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventAnomalyRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookAttemptRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookRedeliveryRepository.class,
        com.swiftdrop.notification.domain.NotificationRecordRepository.class
})
class PricingAndZonesSecurityApiTest {

    private static final UUID COLOMBO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID KANDY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final String PREFIX = "abcdefgh1234";
    private static final String API_KEY = "sdk_" + PREFIX + "_" + "S".repeat(43);
    private static final Instant CREATED_AT = Instant.parse("2026-11-09T08:00:00Z");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtEncoder encoder;

    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private ExternalIdentityRepository identities;
    @MockitoBean
    private GoogleLoginCodeRepository googleCodes;
    @MockitoBean
    private ZoneRepository zones;
    @MockitoBean
    private PricingRuleRepository pricingRules;
    @MockitoBean
    private ParcelRepository parcels;
    @MockitoBean
    private ParcelStatusHistoryRepository parcelHistory;
    @MockitoBean
    private DeliveryProofRepository deliveryProofs;
    @MockitoBean
    private OutboxEventRepository outboxEvents;
    @MockitoBean
    private MerchantRepository merchants;
    @MockitoBean
    private MerchantApiKeyRepository apiKeys;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        givenZone(COLOMBO, "CMB", "Colombo");
        givenZone(KANDY, "KDY", "Kandy");
        PricingRule rule = new PricingRule(COLOMBO, KANDY, new BigDecimal("400.00"), new BigDecimal("100.00"),
                new BigDecimal("0.00"), new BigDecimal("50.00"), new BigDecimal("150.00"), "LKR");
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        when(pricingRules.findByOriginZoneIdAndDestinationZoneIdAndActiveTrue(COLOMBO, KANDY))
                .thenReturn(Optional.of(rule));
        when(zones.findByActiveTrue()).thenReturn(List.of(zone("KDY", "Kandy"), zone("CMB", "Colombo")));

        merchant = new Merchant(UUID.randomUUID(), "Shop", "Retail", "REG-1", "+94771234567", "St", null, null);
        ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));

        when(apiKeys.findByKeyPrefix(PREFIX)).thenReturn(Optional.of(apiKey(API_KEY)));
    }

    private static MerchantApiKey apiKey(String fullKey) {
        MerchantApiKey key = new MerchantApiKey(MERCHANT_ID, PREFIX, MerchantApiKeyFormat.hash(fullKey), "Live",
                UUID.randomUUID(), CREATED_AT);
        ReflectionTestUtils.setField(key, "id", UUID.randomUUID());
        return key;
    }

    // --- price-quotes: JWT ---

    @Test
    @DisplayName("A valid JWT gets a quote, as before")
    void validJwtGetsQuote() throws Exception {
        quote(TestPrincipal.customer().postProcessor())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("650.00"));
    }

    @Test
    @DisplayName("An invalid bearer token is rejected")
    void invalidJwtIsRejected() throws Exception {
        mvc.perform(post("/api/v1/price-quotes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A real, validly signed token (not the {@code jwt()} test post-processor, which injects the
     * {@code SecurityContext} directly and bypasses both the real {@code JwtDecoder} and this
     * chain's {@code jwtAuthenticationConverter()}): proves that converter's {@code UserDirectory}
     * check — disabled or nonexistent account — actually runs on this new chain, not just on
     * {@code apiSecurity}. A garbage token (as in {@code invalidJwtIsRejected}) cannot prove this,
     * because it fails at the decoder stage, before the converter ever runs.
     */
    @Test
    @DisplayName("A validly signed JWT for a disabled or nonexistent user is rejected, not just a malformed one")
    void validlySignedJwtForDisabledOrNonexistentUserIsRejected() throws Exception {
        UUID disabledUserId = UUID.randomUUID();
        User disabled = new User("alice@example.com", "mock-hash", "Alice", java.util.Set.of(Role.CUSTOMER));
        ReflectionTestUtils.setField(disabled, "id", disabledUserId);
        disabled.setEnabled(false);
        when(users.findById(disabledUserId)).thenReturn(Optional.of(disabled));

        UUID nonexistentUserId = UUID.randomUUID();
        when(users.findById(nonexistentUserId)).thenReturn(Optional.empty());

        Instant now = Instant.now();
        String disabledUserToken = token(claimsFor(now, disabledUserId, Role.CUSTOMER));
        String nonexistentUserToken = token(claimsFor(now, nonexistentUserId, Role.CUSTOMER));

        mvc.perform(get("/api/v1/zones").header(HttpHeaders.AUTHORIZATION, "Bearer " + disabledUserToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/zones").header(HttpHeaders.AUTHORIZATION, "Bearer " + nonexistentUserToken))
                .andExpect(status().isUnauthorized());
    }

    // --- price-quotes: merchant API key ---

    @Test
    @DisplayName("A valid, approved merchant's API key also gets a quote")
    void validApiKeyGetsQuote() throws Exception {
        merchant.approve(UUID.randomUUID());

        mvc.perform(withApiKey(post("/api/v1/price-quotes"), API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("650.00"));
    }

    @Test
    @DisplayName("An unknown or malformed API key is rejected")
    void invalidApiKeyIsRejected() throws Exception {
        mvc.perform(withApiKey(post("/api/v1/price-quotes"), "sdk_" + PREFIX + "_" + "T".repeat(43))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody()))
                .andExpect(status().isUnauthorized());
        mvc.perform(withApiKey(post("/api/v1/price-quotes"), "not-a-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A revoked API key is rejected even if the merchant is approved")
    void revokedApiKeyIsRejected() throws Exception {
        merchant.approve(UUID.randomUUID());
        MerchantApiKey revoked = apiKey(API_KEY);
        revoked.revoke(Instant.now());
        when(apiKeys.findByKeyPrefix(PREFIX)).thenReturn(Optional.of(revoked));

        mvc.perform(withApiKey(post("/api/v1/price-quotes"), API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A pending, rejected or suspended merchant's key is rejected")
    void nonApprovedMerchantKeyIsRejected() throws Exception {
        // PENDING (default, never approved)
        mvc.perform(withApiKey(post("/api/v1/price-quotes"), API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody()))
                .andExpect(status().isUnauthorized());

        merchant.reject(UUID.randomUUID(), "Not eligible");
        mvc.perform(withApiKey(get("/api/v1/zones"), API_KEY)).andExpect(status().isUnauthorized());

        merchant.resubmit("Shop", "Retail", "+94771234567", "St", null, null);
        merchant.approve(UUID.randomUUID());
        merchant.suspend(UUID.randomUUID(), "Policy violation");
        mvc.perform(withApiKey(get("/api/v1/zones"), API_KEY)).andExpect(status().isUnauthorized());
    }

    // --- price-quotes / zones: missing or ambiguous credentials ---

    @Test
    @DisplayName("No credentials at all is 401, on both endpoints")
    void missingCredentialsIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/price-quotes").contentType(MediaType.APPLICATION_JSON).content(quoteBody()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/zones")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Both a bearer token and an API key is 400, before either is checked")
    void bothCredentialsIsBadRequest() throws Exception {
        merchant.approve(UUID.randomUUID());

        mvc.perform(withApiKey(get("/api/v1/zones"), API_KEY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer anything-even-invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * The symmetric case of {@link #bothCredentialsIsBadRequest()}: this time the bearer token is
     * valid and the API key is invalid. If {@code AmbiguousCredentialsFilter} did not run before
     * {@code MerchantApiKeyAuthenticationFilter}, that filter would reject the bad key with its own
     * {@code 401} and stop the chain before the ambiguity check ever ran, so this would come back
     * {@code 401} instead of {@code 400}. Combined with {@link #bothCredentialsIsBadRequest()}
     * (invalid bearer, valid key), this proves the filter runs before both.
     */
    @Test
    @DisplayName("A valid bearer token with an invalid API key is still 400, proving the order against both filters")
    void bothCredentialsIsBadRequestEvenWithAnInvalidApiKey() throws Exception {
        UUID userId = UUID.randomUUID();
        User activeCustomer = new User("bob@example.com", "mock-hash", "Bob", java.util.Set.of(Role.CUSTOMER));
        ReflectionTestUtils.setField(activeCustomer, "id", userId);
        when(users.findById(userId)).thenReturn(Optional.of(activeCustomer));
        String validToken = token(claimsFor(Instant.now(), userId, Role.CUSTOMER));

        mvc.perform(withApiKey(get("/api/v1/zones"), "sdk_" + PREFIX + "_" + "T".repeat(43))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("A CORS preflight for the merchant key header is refused: X-Api-Key stays server-to-server only")
    void corsPreflightDoesNotAllowTheApiKeyHeader() throws Exception {
        mvc.perform(options("/api/v1/zones")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, MerchantApiKeyAuthenticationFilter.HEADER))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));

        // A preflight for an allowed header on the same endpoint still succeeds normally, confirming
        // the above is a rejection of the header, not of the endpoint or the preflight mechanism.
        mvc.perform(options("/api/v1/zones")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    // --- zones: JWT and API key ---

    @Test
    @DisplayName("A valid JWT lists active zones, as before")
    void validJwtListsZones() throws Exception {
        mvc.perform(get("/api/v1/zones").with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Colombo"));
    }

    @Test
    @DisplayName("A valid, approved merchant's API key also lists active zones")
    void validApiKeyListsZones() throws Exception {
        merchant.approve(UUID.randomUUID());

        mvc.perform(withApiKey(get("/api/v1/zones"), API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Colombo"));
    }

    // --- regression: existing chains are untouched ---

    @Test
    @DisplayName("A merchant API key still does not authenticate on an ordinary JWT-protected endpoint")
    void apiKeyDoesNotWorkOnOtherEndpoints() throws Exception {
        merchant.approve(UUID.randomUUID());

        mvc.perform(withApiKey(get("/api/v1/users/me"), API_KEY)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A bearer token still does not authenticate on the merchant-order API")
    void jwtDoesNotWorkOnMerchantOrders() throws Exception {
        mvc.perform(post("/api/v1/merchant-orders")
                        .with(TestPrincipal.merchant().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder withApiKey(MockHttpServletRequestBuilder request, String key) {
        return request.header(MerchantApiKeyAuthenticationFilter.HEADER, key);
    }

    private String token(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static JwtClaimsSet claimsFor(Instant now, UUID subject, Role role) {
        return JwtClaimsSet.builder()
                .issuer("swiftdrop")
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("roles", List.of(role.name()))
                .build();
    }

    private org.springframework.test.web.servlet.ResultActions quote(
            org.springframework.test.web.servlet.request.RequestPostProcessor principal) throws Exception {
        return mvc.perform(post("/api/v1/price-quotes").with(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(quoteBody()));
    }

    private static String quoteBody() {
        return "{\"pickupZoneId\":\"" + COLOMBO + "\",\"dropoffZoneId\":\"" + KANDY
                + "\",\"size\":\"MEDIUM\",\"weightGrams\":2000}";
    }

    private void givenZone(UUID id, String code, String name) {
        Zone zone = new Zone(code, name);
        ReflectionTestUtils.setField(zone, "id", id);
        when(zones.findById(id)).thenReturn(Optional.of(zone));
    }

    private static Zone zone(String code, String name) {
        Zone zone = new Zone(code, name);
        ReflectionTestUtils.setField(zone, "id", UUID.randomUUID());
        return zone;
    }
}
