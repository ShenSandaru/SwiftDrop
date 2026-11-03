package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.domain.DeliveryProof;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.TrackingNumberGenerator;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * Status changes against real PostgreSQL with real transactions: status, history and outbox commit
 * or roll back together, concurrent changes cannot share a sequence, and in-process listeners see
 * an event only after commit (ADR 0004 §3, §4.1; ADR 0006 §8).
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
@Import(ParcelStatusChangeIT.CommittedEvents.class)
class ParcelStatusChangeIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private ParcelStatusService service;
    @Autowired
    private ParcelEventRecorder recorder;
    @Autowired
    private ParcelRepository parcels;
    @Autowired
    private DeliveryProofRepository proofs;
    @Autowired
    private ParcelEventSerializer serializer;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CommittedEvents committedEvents;

    // No pricing implementation on this branch; booking needs one to start
    @MockitoBean
    private FeeCalculator feeCalculator;

    private UUID customerId;
    private UUID courierId;
    private UUID parcelId;

    @BeforeEach
    void createAssignedParcel() {
        customerId = insertUser("customer");
        courierId = insertUser("courier");
        parcelId = transactions.execute(status -> {
            Parcel parcel = parcels.save(Parcel.forCustomer(new TrackingNumberGenerator().next(), customerId,
                    address("Colombo"), address("Kandy"), ParcelSize.SMALL, 1000, null,
                    new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), Instant.now()));
            parcels.flush();
            recorder.record(parcel, null, Actor.customer(customerId), null, null, Instant.now());
            return parcel.getId();
        });
        // Assignment arrives with ParcelAssignment (Phase 5)
        jdbc.update("UPDATE parcels SET assigned_courier_id = ?, assigned_at = now() WHERE id = ?", courierId, parcelId);
        committedEvents.clear();
    }

    @Test
    void committedChangeUpdatesParcelHistoryAndOutboxTogether() {
        ParcelStatusChanged event = change(ParcelStatus.PICKED_UP, null, "At the gate");

        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcelId))
                .isEqualTo("PICKED_UP");
        assertThat(jdbc.queryForObject("SELECT last_sequence FROM parcels WHERE id = ?", Integer.class, parcelId))
                .isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT sequence FROM parcel_status_history WHERE parcel_id = ? ORDER BY sequence",
                Integer.class, parcelId)).containsExactly(1, 2);
        assertThat(jdbc.queryForObject("SELECT note FROM parcel_status_history WHERE event_id = ?", String.class,
                event.eventId())).isEqualTo("At the gate");

        String payload = jdbc.queryForObject("SELECT payload::text FROM parcel_outbox_events WHERE event_id = ?",
                String.class, event.eventId());
        assertThat(serializer.fromJson(payload)).isEqualTo(event);
        assertThat(payload).doesNotContain("At the gate");
        assertThat(jdbc.queryForObject("SELECT published_at FROM parcel_outbox_events WHERE event_id = ?",
                Timestamp.class, event.eventId())).isNull();
        assertThat(committedEvents.received()).containsExactly(event);
    }

    @Test
    void rolledBackChangeLeavesNoTraceAndPublishesNothing() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            service.changeStatus(parcelId, new StatusChange(ParcelStatus.PICKED_UP, null, null), Actor.courier(courierId));
            throw new IllegalStateException("simulated failure after the change");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcelId))
                .isEqualTo("CREATED");
        assertThat(countRows("parcel_status_history")).isEqualTo(1);
        assertThat(countRows("parcel_outbox_events")).isEqualTo(1);
        assertThat(committedEvents.received()).isEmpty();
    }

    @Test
    void concurrentPickupsProduceOneChangeAndOneConflict() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> pickup = () -> {
            start.await();
            try {
                service.changeStatus(parcelId, new StatusChange(ParcelStatus.PICKED_UP, null, null),
                        Actor.courier(courierId));
                return "changed";
            } catch (ParcelConflictException e) {
                return "conflict";
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<String> outcomes = new ArrayList<>();
        try {
            Future<String> first = pool.submit(pickup);
            Future<String> second = pool.submit(pickup);
            start.countDown();
            outcomes.add(first.get(30, TimeUnit.SECONDS));
            outcomes.add(second.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(outcomes).containsExactlyInAnyOrder("changed", "conflict");
        assertThat(jdbc.queryForList("SELECT sequence FROM parcel_outbox_events WHERE parcel_id = ? ORDER BY id",
                Integer.class, parcelId)).containsExactly(1, 2);
    }

    @Test
    void deliveredNeedsCommittedProofAndFailedStoresItsReason() {
        change(ParcelStatus.PICKED_UP, null, null);
        change(ParcelStatus.IN_TRANSIT, null, null);
        change(ParcelStatus.OUT_FOR_DELIVERY, null, null);

        assertThatThrownBy(() -> change(ParcelStatus.DELIVERED, null, null))
                .isInstanceOf(ParcelConflictException.class);
        assertThat(countRows("parcel_outbox_events")).isEqualTo(4);

        transactions.executeWithoutResult(status ->
                proofs.save(new DeliveryProof(parcelId, "Kumari Silva", null, null, courierId, Instant.now())));
        ParcelStatusChanged delivered = change(ParcelStatus.DELIVERED, null, null);

        assertThat(delivered.data().sequence()).isEqualTo(5);
        assertThatThrownBy(() -> change(ParcelStatus.FAILED, FailureReason.REFUSED, null))
                .isInstanceOf(ParcelConflictException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcelId))
                .isEqualTo("DELIVERED");
    }

    @Test
    void failureReasonIsStoredInHistoryAndEvent() {
        change(ParcelStatus.PICKED_UP, null, null);
        ParcelStatusChanged failed = change(ParcelStatus.FAILED, FailureReason.ADDRESS_NOT_FOUND, null);

        assertThat(jdbc.queryForObject("SELECT failure_reason_code FROM parcel_status_history WHERE event_id = ?",
                String.class, failed.eventId())).isEqualTo("ADDRESS_NOT_FOUND");
        assertThat(serializer.fromJson(jdbc.queryForObject(
                "SELECT payload::text FROM parcel_outbox_events WHERE event_id = ?", String.class, failed.eventId()))
                .data().failureReasonCode()).isEqualTo(FailureReason.ADDRESS_NOT_FOUND);
    }

    private ParcelStatusChanged change(ParcelStatus target, FailureReason reason, String note) {
        return service.changeStatus(parcelId, new StatusChange(target, reason, note), Actor.courier(courierId));
    }

    private int countRows(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE parcel_id = ?", Integer.class, parcelId);
    }

    private UUID insertUser(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, TRUE, now())",
                id, name + "-" + id + "@example.com", "Test " + name);
        return id;
    }

    private static ParcelAddress address(String city) {
        return new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, city, UUID.randomUUID(), null);
    }

    /** Stands in for {@code tracking}: records events delivered after commit only. */
    @TestConfiguration
    static class CommittedEvents {

        private final List<ParcelStatusChanged> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener
        void onCommitted(ParcelStatusChanged event) {
            events.add(event);
        }

        List<ParcelStatusChanged> received() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }
    }
}
