package com.swiftdrop.merchant.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantApiKey;
import com.swiftdrop.merchant.domain.MerchantApiKeyFormat;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.pricing.application.RouteNotPricedException;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/**
 * Merchant order API (SD-405) through the real API-key chain; delivery's parcel creation and queries
 * are mocked, so this covers HTTP behavior, validation and merchant isolation.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
@MockitoBean(types = {UserRepository.class, ExternalIdentityRepository.class, GoogleLoginCodeRepository.class,
        ZoneRepository.class, PricingRuleRepository.class, ParcelRepository.class, ParcelStatusHistoryRepository.class,
        DeliveryProofRepository.class, OutboxEventRepository.class, ParcelAssignmentRepository.class,
        MerchantStatusHistoryRepository.class, MerchantWebhookEndpointRepository.class})
class MerchantOrderApiTest {

    private static final String ORDERS = "/api/v1/merchant-orders";
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID PARCEL_ID = UUID.randomUUID();
    private static final UUID ZONE_ID = UUID.randomUUID();
    private static final String PREFIX = "abcdefgh1234";
    private static final String API_KEY = "sdk_" + PREFIX + "_" + "S".repeat(43);
    private static final Instant CREATED_AT = Instant.parse("2026-11-09T08:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MerchantRepository merchants;
    @MockitoBean
    private MerchantApiKeyRepository apiKeys;
    @MockitoBean
    private ParcelCreation parcelCreation;
    @MockitoBean
    private ParcelQueries parcelQueries;

    @BeforeEach
    void setUp() {
        Merchant merchant = new Merchant(UUID.randomUUID(), "Shop", "Retail", "REG-1", "+94771234567", "St", null, null);
        ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
        merchant.approve(UUID.randomUUID());
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));

        MerchantApiKey key = new MerchantApiKey(MERCHANT_ID, PREFIX, MerchantApiKeyFormat.hash(API_KEY), "Live",
                UUID.randomUUID(), CREATED_AT);
        ReflectionTestUtils.setField(key, "id", UUID.randomUUID());
        when(apiKeys.findByKeyPrefix(PREFIX)).thenReturn(Optional.of(key));
    }

    @Test
    @DisplayName("A new order answers 201 with a Location, the tracking number and the fee")
    void createNew() throws Exception {
        when(parcelCreation.createMerchantParcel(any())).thenReturn(created(true));

        mvc.perform(withKey(post(ORDERS)).contentType(MediaType.APPLICATION_JSON).content(orderJson("ORD-10045")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/api/v1/merchant-orders/ORD-10045"))
                .andExpect(jsonPath("$.id").value(PARCEL_ID.toString()))
                .andExpect(jsonPath("$.merchantOrderReference").value("ORD-10045"))
                .andExpect(jsonPath("$.trackingNumber").value("SD7K3M9Q2XWB"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.fee.amount").value("650.00"))
                .andExpect(jsonPath("$.fee.currency").value("LKR"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-09T08:00:00Z"));
    }

    @Test
    @DisplayName("The order passed to delivery uses the key's merchant, never a merchant in the body")
    void merchantFromKey() throws Exception {
        when(parcelCreation.createMerchantParcel(any())).thenReturn(created(true));
        String body = orderJson("ORD-1").replaceFirst("\\{", "{\"merchantId\":\"" + UUID.randomUUID() + "\",");

        mvc.perform(withKey(post(ORDERS)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        ArgumentCaptor<NewMerchantParcel> order = ArgumentCaptor.forClass(NewMerchantParcel.class);
        verify(parcelCreation).createMerchantParcel(order.capture());
        assertThat(order.getValue().merchantId()).isEqualTo(MERCHANT_ID);
        assertThat(order.getValue().merchantOrderReference()).isEqualTo("ORD-1");
        assertThat(order.getValue().size()).isEqualTo(ParcelSize.MEDIUM);
        assertThat(order.getValue().weightGrams()).isEqualTo(1200);
        assertThat(order.getValue().pickup().zoneId()).isEqualTo(ZONE_ID);
    }

    @Test
    @DisplayName("Repeating a reference answers 200 with the existing parcel and no Location (D-25)")
    void duplicateReference() throws Exception {
        when(parcelCreation.createMerchantParcel(any())).thenReturn(created(false));

        mvc.perform(withKey(post(ORDERS)).contentType(MediaType.APPLICATION_JSON).content(orderJson("ORD-10045")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").value(PARCEL_ID.toString()))
                .andExpect(jsonPath("$.trackingNumber").value("SD7K3M9Q2XWB"));
    }

    @Test
    @DisplayName("Invalid bodies answer 400 listing the invalid fields, before delivery is called")
    void invalidBody() throws Exception {
        String missingAll = "{}";
        String badFields = orderJson("ORD 1")                       // space not allowed in a reference
                .replace("\"weightGrams\": 1200", "\"weightGrams\": 0")
                .replace("+94771234567", "0771234567");

        mvc.perform(withKey(post(ORDERS)).contentType(MediaType.APPLICATION_JSON).content(missingAll))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields").isArray());
        mvc.perform(withKey(post(ORDERS)).contentType(MediaType.APPLICATION_JSON).content(badFields))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields[?(@ == 'merchantOrderReference')]").exists())
                .andExpect(jsonPath("$.invalidFields[?(@ == 'weightGrams')]").exists())
                .andExpect(jsonPath("$.invalidFields[?(@ == 'pickup.phone')]").exists());
        mvc.perform(withKey(post(ORDERS)).contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
        verify(parcelCreation, never()).createMerchantParcel(any());
    }

    @Test
    @DisplayName("An unknown size or a delivery validation failure answers a generic 400")
    void deliveryRejects() throws Exception {
        String hugeSize = orderJson("ORD-2").replace("\"MEDIUM\"", "\"HUGE\"");
        mvc.perform(withKey(post(ORDERS)).contentType(MediaType.APPLICATION_JSON).content(hugeSize))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid request"));
        verify(parcelCreation, never()).createMerchantParcel(any());

        when(parcelCreation.createMerchantParcel(any())).thenThrow(new IllegalArgumentException("pickup zone unknown"));
        mvc.perform(withKey(post(ORDERS)).contentType(MediaType.APPLICATION_JSON).content(orderJson("ORD-3")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid request"));
    }

    @Test
    @DisplayName("A route without pricing answers 409")
    void routeNotPriced() throws Exception {
        // Mockito creates the exception: its constructor is internal to pricing.
        when(parcelCreation.createMerchantParcel(any())).thenThrow(RouteNotPricedException.class);

        mvc.perform(withKey(post(ORDERS)).contentType(MediaType.APPLICATION_JSON).content(orderJson("ORD-4")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET returns the merchant's own order by reference")
    void getOwnOrder() throws Exception {
        when(parcelQueries.findByMerchantReference(MERCHANT_ID, "ORD-10045")).thenReturn(Optional.of(new ParcelSummary(
                PARCEL_ID, "SD7K3M9Q2XWB", ParcelStatus.IN_TRANSIT, ParcelSize.SMALL, 800, ParcelSource.MERCHANT,
                null, MERCHANT_ID, "ORD-10045", UUID.randomUUID(), CREATED_AT)));

        mvc.perform(withKey(get(ORDERS + "/ORD-10045")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PARCEL_ID.toString()))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.size").value("SMALL"))
                .andExpect(jsonPath("$.weightGrams").value(800))
                .andExpect(jsonPath("$.assignedCourierId").doesNotExist());
    }

    @Test
    @DisplayName("An unknown reference or another merchant's order answers 404")
    void getUnknownOrder() throws Exception {
        when(parcelQueries.findByMerchantReference(eq(MERCHANT_ID), any())).thenReturn(Optional.empty());

        mvc.perform(withKey(get(ORDERS + "/OTHER-MERCHANT-ORDER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Order not found"));
    }

    @Test
    @DisplayName("Without a valid API key, or with a user token, both endpoints answer 401")
    void requiresApiKey() throws Exception {
        mvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON).content(orderJson("ORD-5")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(ORDERS + "/ORD-5").with(TestPrincipal.merchant(UUID.randomUUID()).postProcessor()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(ORDERS + "/ORD-5").header("X-Api-Key", "sdk_" + PREFIX + "_" + "T".repeat(43)))
                .andExpect(status().isUnauthorized());
        verify(parcelCreation, never()).createMerchantParcel(any());
    }

    private static MockHttpServletRequestBuilder withKey(MockHttpServletRequestBuilder request) {
        return request.header("X-Api-Key", API_KEY);
    }

    private static CreatedParcel created(boolean newlyCreated) {
        return new CreatedParcel(PARCEL_ID, "SD7K3M9Q2XWB", ParcelStatus.CREATED, new BigDecimal("650.00"), "LKR",
                CREATED_AT, newlyCreated);
    }

    private static String orderJson(String reference) {
        return """
                {
                  "merchantOrderReference": "%s",
                  "pickup": {"contactName": "Store", "phone": "+94771234567", "line1": "1 Main St",
                             "city": "Colombo", "zoneId": "%s"},
                  "dropoff": {"contactName": "Nimal", "phone": "+94779876543", "line1": "2 Hill Rd",
                              "city": "Kandy", "zoneId": "%s"},
                  "size": "MEDIUM",
                  "weightGrams": 1200,
                  "description": "Shoes"
                }
                """.formatted(reference, ZONE_ID, ZONE_ID);
    }
}
