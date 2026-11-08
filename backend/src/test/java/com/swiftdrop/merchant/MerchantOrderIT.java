package com.swiftdrop.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.test.security.TestPrincipal;

/**
 * Merchant order intake end to end against real PostgreSQL (SD-403 to SD-405): a key created over
 * HTTP, an order placed with it, the stored parcel, history and outbox event, idempotency per
 * reference, isolation between merchants, and revocation. Pricing is mocked as in ParcelBookingIT.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@AutoConfigureMockMvc
@Testcontainers
class MerchantOrderIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MerchantRepository merchants;

    @MockitoBean
    private FeeCalculator feeCalculator;

    @BeforeEach
    void setUp() {
        when(feeCalculator.calculateFee(any(), any(), any(), anyInt())).thenReturn(new FeeQuote(
                new BigDecimal("650.00"), "LKR", new BigDecimal("400.00"), new BigDecimal("150.00"),
                new BigDecimal("100.00"), UUID.randomUUID()));
    }

    @Test
    void approvedMerchantCreatesAndReadsOrdersWithAnApiKey() throws Exception {
        UUID userId = insertUser();
        Merchant merchant = approvedMerchant(userId);
        String apiKey = createKey(userId);
        String reference = "ORD-" + UUID.randomUUID().toString().substring(0, 8);

        String body = mvc.perform(post("/api/v1/merchant-orders").header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON).content(orderJson(reference)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fee.amount").value("650.00"))
                .andReturn().getResponse().getContentAsString();
        UUID parcelId = UUID.fromString(JsonPath.read(body, "$.id"));

        assertThat(jdbc.queryForMap("SELECT source, merchant_id, merchant_order_reference, customer_id, status "
                + "FROM parcels WHERE id = ?", parcelId))
                .containsEntry("source", "MERCHANT")
                .containsEntry("merchant_id", merchant.getId())
                .containsEntry("merchant_order_reference", reference)
                .containsEntry("customer_id", null)
                .containsEntry("status", "CREATED");
        assertThat(jdbc.queryForObject("SELECT actor_role FROM parcel_status_history WHERE parcel_id = ?",
                String.class, parcelId)).isEqualTo("MERCHANT");
        String event = jdbc.queryForObject("SELECT payload::text FROM parcel_outbox_events WHERE parcel_id = ?",
                String.class, parcelId);
        assertThat(event).contains(merchant.getId().toString(), reference).doesNotContain("Nimal", "+9477");

        // The same reference again: the existing parcel, no second parcel or event (D-25)
        mvc.perform(post("/api/v1/merchant-orders").header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON).content(orderJson(reference)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(parcelId.toString()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parcels WHERE merchant_order_reference = ?",
                Integer.class, reference)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parcel_outbox_events WHERE parcel_id = ?",
                Integer.class, parcelId)).isEqualTo(1);

        mvc.perform(get("/api/v1/merchant-orders/" + reference).header("X-Api-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(parcelId.toString()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.size").value("MEDIUM"));

        // last_used_at was recorded by the authenticated requests
        assertThat(jdbc.queryForObject("SELECT last_used_at IS NOT NULL FROM merchant_api_keys WHERE merchant_id = ?",
                Boolean.class, merchant.getId())).isTrue();
    }

    @Test
    void merchantsAreIsolatedAndRevokedKeysStopWorking() throws Exception {
        UUID firstUser = insertUser();
        approvedMerchant(firstUser);
        String firstKey = createKey(firstUser);
        UUID secondUser = insertUser();
        approvedMerchant(secondUser);
        String secondKey = createKey(secondUser);
        String reference = "SHARED-" + UUID.randomUUID().toString().substring(0, 8);

        String firstBody = mvc.perform(post("/api/v1/merchant-orders").header("X-Api-Key", firstKey)
                        .contentType(MediaType.APPLICATION_JSON).content(orderJson(reference)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Another merchant cannot see the order, and may use the same reference for its own order
        mvc.perform(get("/api/v1/merchant-orders/" + reference).header("X-Api-Key", secondKey))
                .andExpect(status().isNotFound());
        String secondBody = mvc.perform(post("/api/v1/merchant-orders").header("X-Api-Key", secondKey)
                        .contentType(MediaType.APPLICATION_JSON).content(orderJson(reference)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(secondBody, "$.id")).isNotEqualTo(JsonPath.read(firstBody, "$.id"));

        // Revoking the key stops it on the next request
        String keyId = JsonPath.read(mvc.perform(get("/api/v1/merchants/me/api-keys")
                        .with(TestPrincipal.merchant(firstUser).postProcessor()))
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        mvc.perform(delete("/api/v1/merchants/me/api-keys/" + keyId)
                        .with(TestPrincipal.merchant(firstUser).postProcessor()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/merchant-orders/" + reference).header("X-Api-Key", firstKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suspendedMerchantsKeysStopWorking() throws Exception {
        UUID userId = insertUser();
        Merchant merchant = approvedMerchant(userId);
        String apiKey = createKey(userId);

        merchant.suspend(insertUser(), "Review");
        merchants.saveAndFlush(merchant);

        mvc.perform(post("/api/v1/merchant-orders").header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON).content(orderJson("ORD-SUSPENDED")))
                .andExpect(status().isUnauthorized());
    }

    private String createKey(UUID userId) throws Exception {
        String body = mvc.perform(post("/api/v1/merchants/me/api-keys")
                        .with(TestPrincipal.merchant(userId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Integration\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.apiKey");
    }

    private Merchant approvedMerchant(UUID userId) {
        Merchant merchant = new Merchant(userId, "Shop " + userId, "Retail", "REG-" + userId, "+94771234567",
                "Main St", null, null);
        merchant.approve(insertUser()); // decided_by references the reviewing admin's user row
        return merchants.saveAndFlush(merchant);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) "
                + "VALUES (?, ?, ?, ?, ?, NOW())", userId, "merchant" + userId + "@example.com", "hash", "Merchant", true);
        return userId;
    }

    private static String orderJson(String reference) {
        UUID zone = UUID.randomUUID();
        return """
                {
                  "merchantOrderReference": "%s",
                  "pickup": {"contactName": "Store", "phone": "+94771234567", "line1": "1 Main St",
                             "city": "Colombo", "zoneId": "%s"},
                  "dropoff": {"contactName": "Nimal", "phone": "+94779876543", "line1": "2 Hill Rd",
                              "city": "Kandy", "zoneId": "%s"},
                  "size": "MEDIUM",
                  "weightGrams": 1200
                }
                """.formatted(reference, zone, zone);
    }
}
