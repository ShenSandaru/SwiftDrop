package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.test.security.TestPrincipal;

/**
 * Booking end to end against real PostgreSQL: HTTP request, parcel, first history entry and outbox
 * event in one transaction, ownership on reads, and merchant idempotency under a race. Pricing is
 * mocked because this branch has no fee calculator.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@AutoConfigureMockMvc
@Testcontainers
class ParcelBookingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ParcelCreation parcelCreation;
    @Autowired
    private ParcelEventSerializer serializer;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = insertUser("customer");
        when(feeCalculator.calculateFee(any(), any(), any(), anyInt())).thenReturn(new FeeQuote(
                new BigDecimal("850.00"), "LKR", new BigDecimal("550.00"), new BigDecimal("200.00"),
                new BigDecimal("100.00"), UUID.randomUUID()));
    }

    @Test
    void customerBooksOverHttpAndParcelHistoryAndOutboxAreStoredTogether() throws Exception {
        String body = mvc.perform(post("/api/v1/parcels").with(TestPrincipal.customer(customerId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = JSON.readTree(body);
        UUID parcelId = UUID.fromString(created.get("id").asString());

        assertThat(jdbc.queryForMap("SELECT customer_id, status, fee_amount, last_sequence FROM parcels WHERE id = ?",
                parcelId))
                .containsEntry("customer_id", customerId)
                .containsEntry("status", "CREATED")
                .containsEntry("fee_amount", new BigDecimal("850.00"))
                .containsEntry("last_sequence", 1);
        assertThat(jdbc.queryForMap("SELECT sequence, from_status, to_status, actor_role, actor_user_id "
                + "FROM parcel_status_history WHERE parcel_id = ?", parcelId))
                .containsEntry("sequence", 1)
                .containsEntry("from_status", null)
                .containsEntry("to_status", "CREATED")
                .containsEntry("actor_role", "CUSTOMER")
                .containsEntry("actor_user_id", customerId);
        String payload = jdbc.queryForObject("SELECT payload::text FROM parcel_outbox_events WHERE parcel_id = ?",
                String.class, parcelId);
        assertThat(serializer.fromJson(payload).data().newStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(serializer.fromJson(payload).data().ownerCustomerId()).isEqualTo(customerId);
        assertThat(payload).doesNotContain("Nimal", "+9477", "Temple");

        mvc.perform(get("/api/v1/parcels").with(TestPrincipal.customer(customerId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(parcelId.toString()))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/parcels/" + parcelId).with(TestPrincipal.customer(customerId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[0].status").value("CREATED"));
        mvc.perform(get("/api/v1/parcels/" + parcelId + "/receipt")
                        .with(TestPrincipal.customer(customerId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee.amount").value("850.00"));
    }

    @Test
    void anotherCustomerSeesNeitherTheParcelNorItInTheirList() throws Exception {
        String body = mvc.perform(post("/api/v1/parcels").with(TestPrincipal.customer(customerId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingJson()))
                .andReturn().getResponse().getContentAsString();
        String parcelId = JSON.readTree(body).get("id").asString();
        UUID otherCustomer = insertUser("other");

        mvc.perform(get("/api/v1/parcels/" + parcelId).with(TestPrincipal.customer(otherCustomer).postProcessor()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/parcels/" + parcelId + "/receipt")
                        .with(TestPrincipal.customer(otherCustomer).postProcessor()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/parcels").with(TestPrincipal.customer(otherCustomer).postProcessor()))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void identicalMerchantOrdersAtTheSameTimeCreateOneParcelAndOneEvent() throws Exception {
        UUID merchantId = UUID.randomUUID();
        NewMerchantParcel order = new NewMerchantParcel(merchantId, "ORD-RACE", contact(), contact(),
                ParcelSize.SMALL, 1000, null);
        CountDownLatch start = new CountDownLatch(1);
        Callable<CreatedParcel> submit = () -> {
            start.await();
            return parcelCreation.createMerchantParcel(order);
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<CreatedParcel> results = new ArrayList<>();
        try {
            Future<CreatedParcel> first = pool.submit(submit);
            Future<CreatedParcel> second = pool.submit(submit);
            start.countDown();
            results.add(first.get(30, TimeUnit.SECONDS));
            results.add(second.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(results).extracting(CreatedParcel::parcelId).containsOnly(results.getFirst().parcelId());
        assertThat(results).extracting(CreatedParcel::newlyCreated).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parcels WHERE merchant_id = ?", Integer.class, merchantId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parcel_outbox_events WHERE parcel_id = ?", Integer.class,
                results.getFirst().parcelId())).isEqualTo(1);

        CreatedParcel repeated = parcelCreation.createMerchantParcel(order);
        assertThat(repeated.newlyCreated()).isFalse();
        assertThat(repeated.parcelId()).isEqualTo(results.getFirst().parcelId());
    }

    private UUID insertUser(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, TRUE, now())",
                id, name + "-" + id + "@example.com", "Test " + name);
        return id;
    }

    private static DeliveryContact contact() {
        return new DeliveryContact("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo", UUID.randomUUID(),
                null);
    }

    private static String bookingJson() {
        String address = "{\"contactName\":\"Nimal Perera\",\"phone\":\"+94771234567\",\"line1\":\"12 Temple Rd\","
                + "\"city\":\"Colombo\",\"zoneId\":\"" + UUID.randomUUID() + "\"}";
        return "{\"pickup\":" + address + ",\"dropoff\":" + address + ",\"size\":\"MEDIUM\",\"weightGrams\":2000}";
    }
}
