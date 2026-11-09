package com.swiftdrop.dispatch.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.delivery.domain.TrackingNumberGenerator;
import com.swiftdrop.test.security.TestPrincipal;

/**
 * SD-317: Multi-threaded integration tests validating concurrent courier assignments,
 * optimistic locking, and conflict handling (HTTP 409).
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@AutoConfigureMockMvc
@Testcontainers
class DispatchAssignmentConcurrencyIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final TrackingNumberGenerator trackingNumbers = new TrackingNumberGenerator();
    private UUID customerId;
    private UUID dispatcherId;

    @BeforeEach
    void setUp() {
        customerId = insertUser("customer", "CUSTOMER");
        dispatcherId = insertUser("dispatcher", "DISPATCHER");
    }

    @Test
    void twoConcurrentAssignmentsForSameParcel_resultInOneSuccessAndOneConflict() throws Exception {
        UUID courier1 = insertUser("courier-1", "COURIER");
        UUID courier2 = insertUser("courier-2", "COURIER");
        UUID parcelId = insertParcel("CREATED");

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        UUID[] couriers = new UUID[] {courier1, courier2};
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final UUID assignedCourier = couriers[i];
            tasks.add(() -> {
                readyLatch.countDown();
                // Wait for all threads to be ready to execute at the exact same moment
                if (!startLatch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timeout waiting for startLatch");
                }
                String body = toJson(parcelId, assignedCourier, "PICKUP");

                MvcResult result = mvc.perform(post("/api/v1/dispatch/parcel-assignments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(TestPrincipal.dispatcherAdmin(dispatcherId).postProcessor()))
                        .andReturn();
                return result.getResponse().getStatus();
            });
        }

        // Wait until both tasks are ready
        readyLatch.await(5, TimeUnit.SECONDS);
        // Release both tasks concurrently
        startLatch.countDown();

        List<Future<Integer>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }

        Collections.sort(statuses);
        // Exactly one 201 Created and one 409 Conflict
        assertThat(statuses).containsExactly(HttpStatus.CREATED.value(), HttpStatus.CONFLICT.value());

        // Verify exactly 1 active assignment exists for the parcel in the database
        Integer activeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM parcel_assignments WHERE parcel_id = ? AND status = 'ACTIVE'",
                Integer.class, parcelId);
        assertThat(activeCount).isEqualTo(1);

        // Verify total assignments count for parcel is 1 (no orphaned or duplicate rows)
        Integer totalCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM parcel_assignments WHERE parcel_id = ?",
                Integer.class, parcelId);
        assertThat(totalCount).isEqualTo(1);

        // Verify parcels table has the assigned courier
        UUID assignedInDb = jdbc.queryForObject(
                "SELECT assigned_courier_id FROM parcels WHERE id = ?",
                UUID.class, parcelId);
        assertThat(assignedInDb).isIn(courier1, courier2);
    }

    @Test
    void reassigningParcelToDifferentCourier_persistsPreviousReassignedAndNewActiveRecord() throws Exception {
        UUID courier1 = insertUser("courier-alpha", "COURIER");
        UUID courier2 = insertUser("courier-beta", "COURIER");
        UUID parcelId = insertParcel("CREATED");

        // Initial assignment to courier1
        String body1 = toJson(parcelId, courier1, "PICKUP");
        mvc.perform(post("/api/v1/dispatch/parcel-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1)
                        .with(TestPrincipal.dispatcherAdmin(dispatcherId).postProcessor()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courierId").value(courier1.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());

        // Sequential reassignment to courier2
        String body2 = toJson(parcelId, courier2, "PICKUP");
        mvc.perform(post("/api/v1/dispatch/parcel-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2)
                        .with(TestPrincipal.dispatcherAdmin(dispatcherId).postProcessor()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courierId").value(courier2.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());

        // Verify parcels table has the new courier assigned
        UUID assignedInDb = jdbc.queryForObject(
                "SELECT assigned_courier_id FROM parcels WHERE id = ?",
                UUID.class, parcelId);
        assertThat(assignedInDb).isEqualTo(courier2);

        // Verify total assignments count for parcel is 2
        Integer totalCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM parcel_assignments WHERE parcel_id = ?",
                Integer.class, parcelId);
        assertThat(totalCount).isEqualTo(2);

        // Verify exactly 1 ACTIVE assignment exists
        Integer activeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM parcel_assignments WHERE parcel_id = ? AND status = 'ACTIVE'",
                Integer.class, parcelId);
        assertThat(activeCount).isEqualTo(1);

        // Verify exactly 1 REASSIGNED assignment exists with completed_at set
        Integer reassignedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM parcel_assignments WHERE parcel_id = ? AND status = 'REASSIGNED' AND completed_at IS NOT NULL",
                Integer.class, parcelId);
        assertThat(reassignedCount).isEqualTo(1);

        // Verify the previous assignment belongs to courier1 and the active assignment belongs to courier2
        UUID activeCourierInDb = jdbc.queryForObject(
                "SELECT courier_id FROM parcel_assignments WHERE parcel_id = ? AND status = 'ACTIVE'",
                UUID.class, parcelId);
        assertThat(activeCourierInDb).isEqualTo(courier2);

        UUID reassignedCourierInDb = jdbc.queryForObject(
                "SELECT courier_id FROM parcel_assignments WHERE parcel_id = ? AND status = 'REASSIGNED'",
                UUID.class, parcelId);
        assertThat(reassignedCourierInDb).isEqualTo(courier1);
    }

    @Test
    void retryingSameAssignmentReturnsExistingAssignmentIdempotently() throws Exception {
        UUID courier = insertUser("courier-idempotent", "COURIER");
        UUID parcelId = insertParcel("CREATED");

        String body = toJson(parcelId, courier, "PICKUP");

        // First assignment
        mvc.perform(post("/api/v1/dispatch/parcel-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(TestPrincipal.dispatcherAdmin(dispatcherId).postProcessor()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courierId").value(courier.toString()));

        // Retry same assignment
        mvc.perform(post("/api/v1/dispatch/parcel-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(TestPrincipal.dispatcherAdmin(dispatcherId).postProcessor()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courierId").value(courier.toString()));

        // Verify only 1 assignment record exists
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM parcel_assignments WHERE parcel_id = ?",
                Integer.class, parcelId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void concurrentAssignmentsForDifferentParcels_bothSucceed() throws Exception {
        UUID courier1 = insertUser("courier-p1", "COURIER");
        UUID courier2 = insertUser("courier-p2", "COURIER");
        UUID parcel1 = insertParcel("CREATED");
        UUID parcel2 = insertParcel("CREATED");

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Callable<Integer>> tasks = List.of(
                () -> {
                    readyLatch.countDown();
                    startLatch.await(5, TimeUnit.SECONDS);
                    String body = toJson(parcel1, courier1, "PICKUP");
                    return mvc.perform(post("/api/v1/dispatch/parcel-assignments")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .with(TestPrincipal.dispatcherAdmin(dispatcherId).postProcessor()))
                            .andReturn().getResponse().getStatus();
                },
                () -> {
                    readyLatch.countDown();
                    startLatch.await(5, TimeUnit.SECONDS);
                    String body = toJson(parcel2, courier2, "PICKUP");
                    return mvc.perform(post("/api/v1/dispatch/parcel-assignments")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .with(TestPrincipal.dispatcherAdmin(dispatcherId).postProcessor()))
                            .andReturn().getResponse().getStatus();
                }
        );

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        List<Future<Integer>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        for (Future<Integer> future : futures) {
            assertThat(future.get()).isEqualTo(HttpStatus.CREATED.value());
        }

        assertThat(jdbc.queryForObject("SELECT assigned_courier_id FROM parcels WHERE id = ?", UUID.class, parcel1))
                .isEqualTo(courier1);
        assertThat(jdbc.queryForObject("SELECT assigned_courier_id FROM parcels WHERE id = ?", UUID.class, parcel2))
                .isEqualTo(courier2);
    }

    @Test
    void oneCourierCanReceiveMultipleActiveParcelsConcurrently() throws Exception {
        UUID courier = insertUser("courier-multi", "COURIER");
        UUID parcel1 = insertParcel("CREATED");
        UUID parcel2 = insertParcel("CREATED");

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        UUID[] parcelIds = new UUID[] {parcel1, parcel2};
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final UUID pId = parcelIds[i];
            tasks.add(() -> {
                readyLatch.countDown();
                startLatch.await(5, TimeUnit.SECONDS);
                String body = toJson(pId, courier, "PICKUP");
                return mvc.perform(post("/api/v1/dispatch/parcel-assignments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(TestPrincipal.dispatcherAdmin(dispatcherId).postProcessor()))
                        .andReturn().getResponse().getStatus();
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        List<Future<Integer>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        for (Future<Integer> future : futures) {
            assertThat(future.get()).isEqualTo(HttpStatus.CREATED.value());
        }

        Integer courierActiveCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM parcel_assignments WHERE courier_id = ? AND status = 'ACTIVE'",
                Integer.class, courier);
        assertThat(courierActiveCount).isEqualTo(2);
    }

    @Test
    void assignmentRejectedWith409IfParcelProgressedPastCreated() throws Exception {
        UUID courier = insertUser("courier-progressed", "COURIER");
        UUID parcelId = insertParcel("PICKED_UP");

        String body = toJson(parcelId, courier, "PICKUP");

        mvc.perform(post("/api/v1/dispatch/parcel-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(TestPrincipal.dispatcherAdmin(dispatcherId).postProcessor()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A courier can only be assigned before pickup"));
    }

    private static String toJson(UUID parcelId, UUID courierId, String type) {
        return String.format("{\"parcelId\":\"%s\",\"courierId\":\"%s\",\"type\":\"%s\"}",
                parcelId, courierId, type);
    }

    private UUID insertUser(String namePrefix, String role) {
        UUID id = UUID.randomUUID();
        String email = (namePrefix + "-" + id + "@example.com").toLowerCase();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, TRUE, ?)",
                id, email, "Test " + namePrefix, Timestamp.from(NOW));
        jdbc.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, ?)", id, role);
        return id;
    }

    private UUID insertParcel(String status) {
        UUID id = UUID.randomUUID();
        String trackingNumber = trackingNumbers.next();
        UUID zoneId = UUID.randomUUID();
        UUID pricingRuleId = UUID.randomUUID();
        UUID assignedCourier = "CREATED".equals(status) ? null : insertUser("initial-courier", "COURIER");
        Timestamp assignedAt = assignedCourier == null ? null : Timestamp.from(NOW);
        jdbc.update("""
                INSERT INTO parcels (
                    id, tracking_number, source, customer_id, assigned_courier_id, assigned_at, pickup_contact_name, pickup_phone,
                    pickup_line1, pickup_city, pickup_zone_id, dropoff_contact_name, dropoff_phone, dropoff_line1,
                    dropoff_city, dropoff_zone_id, size, weight_grams, fee_amount, fee_currency, pricing_rule_id, status,
                    last_sequence, created_at, created_by, updated_at, version
                ) VALUES (
                    ?, ?, 'CUSTOMER', ?, ?, ?, 'Nimal Perera', '+94771234567',
                    '12 Temple Rd', 'Colombo', ?, 'Sunil Fernando', '+94777654321', '45 Galle Rd',
                    'Colombo', ?, 'SMALL', 1000, 650.00, 'LKR', ?, ?,
                    1, ?, ?, ?, 0
                )
                """,
                id, trackingNumber, customerId, assignedCourier, assignedAt, zoneId, zoneId, pricingRuleId, status,
                Timestamp.from(NOW), customerId, Timestamp.from(NOW));
        return id;
    }
}
