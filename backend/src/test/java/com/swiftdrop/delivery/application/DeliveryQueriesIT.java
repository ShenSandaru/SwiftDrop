package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.delivery.api.PublicTrackingView;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * The {@code delivery.api} contracts other modules use, against real PostgreSQL: assignment rules,
 * unassigned and workload queries, merchant isolation, public tracking and event rebuilding.
 * Every test uses its own merchant or couriers, so tests do not see each other's parcels.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
class DeliveryQueriesIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private ParcelCreation creation;
    @Autowired
    private ParcelAssignment assignment;
    @Autowired
    private ParcelQueries queries;
    @Autowired
    private ParcelEvents events;
    @Autowired
    private ParcelStatusService statusService;
    @Autowired
    private ParcelEventSerializer serializer;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private UUID merchantId;
    private UUID dispatcherId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        dispatcherId = insertUser("dispatcher");
        when(feeCalculator.calculateFee(any(), any(), any(), anyInt())).thenReturn(new FeeQuote(
                new BigDecimal("650.00"), "LKR", new BigDecimal("400.00"), new BigDecimal("100.00"),
                new BigDecimal("150.00"), UUID.randomUUID()));
    }

    @Test
    void assignmentWorksWhileCreatedAndIsRefusedAfterPickup() {
        UUID parcelId = order("ORD-A").parcelId();
        UUID first = insertUser("courier");
        UUID second = insertUser("courier");

        assignment.assignCourier(parcelId, first, dispatcherId);
        assignment.assignCourier(parcelId, second, dispatcherId);
        assertThat(jdbc.queryForObject("SELECT assigned_courier_id FROM parcels WHERE id = ?", UUID.class, parcelId))
                .isEqualTo(second);
        // Assignment is not a status change: no new history entry or event
        assertThat(jdbc.queryForObject("SELECT last_sequence FROM parcels WHERE id = ?", Integer.class, parcelId))
                .isEqualTo(1);

        statusService.changeStatus(parcelId, new StatusChange(ParcelStatus.PICKED_UP, null, null), Actor.courier(second));
        assertThatThrownBy(() -> assignment.assignCourier(parcelId, first, dispatcherId))
                .isInstanceOf(ParcelConflictException.class);
    }

    @Test
    void unassignedQueryListsOnlyWaitingParcelsOldestFirst() {
        UUID oldest = order("ORD-1").parcelId();
        UUID assigned = order("ORD-2").parcelId();
        UUID newest = order("ORD-3").parcelId();
        assignment.assignCourier(assigned, insertUser("courier"), dispatcherId);

        List<UUID> waiting = queries.findUnassignedParcels(new PageRequest(0, 100)).items().stream()
                .filter(summary -> merchantId.equals(summary.merchantId()))
                .map(ParcelSummary::id)
                .toList();

        assertThat(waiting).containsExactly(oldest, newest);
    }

    @Test
    void workloadCountsOnlyUnfinishedParcels() {
        UUID busy = insertUser("courier");
        UUID idle = insertUser("courier");
        UUID delivering = order("ORD-W1").parcelId();
        UUID waiting = order("ORD-W2").parcelId();
        UUID failing = order("ORD-W3").parcelId();
        for (UUID parcel : List.of(delivering, waiting, failing)) {
            assignment.assignCourier(parcel, busy, dispatcherId);
        }
        statusService.changeStatus(failing, new StatusChange(ParcelStatus.PICKED_UP, null, null), Actor.courier(busy));
        statusService.changeStatus(failing, new StatusChange(ParcelStatus.FAILED,
                FailureReason.DAMAGED, null), Actor.courier(busy));

        assertThat(queries.countActiveParcelsByCourier())
                .contains(entry(busy, 2L))
                .doesNotContainKey(idle);
    }

    @Test
    void merchantsSeeOnlyTheirOwnParcels() {
        order("ORD-M1");
        order("ORD-M2");
        UUID otherMerchant = UUID.randomUUID();
        creation.createMerchantParcel(new NewMerchantParcel(otherMerchant, "ORD-M1", contact("Galle"), contact("Jaffna"),
                ParcelSize.SMALL, 1000, null));

        assertThat(queries.findParcelsByMerchant(merchantId, null, new PageRequest(0, 20)).items())
                .hasSize(2)
                .allSatisfy(summary -> assertThat(summary.merchantId()).isEqualTo(merchantId));
        assertThat(queries.findParcelsByMerchant(merchantId, ParcelStatus.DELIVERED, new PageRequest(0, 20)).items())
                .isEmpty();
        assertThat(queries.findByMerchantReference(merchantId, "ORD-M1")).isPresent();
        assertThat(queries.findByMerchantReference(UUID.randomUUID(), "ORD-M1")).isEmpty();
    }

    @Test
    void publicViewShowsTimelineWithoutPersonalData() {
        CreatedParcel created = order("ORD-P");
        UUID courier = insertUser("courier");
        assignment.assignCourier(created.parcelId(), courier, dispatcherId);
        statusService.changeStatus(created.parcelId(), new StatusChange(ParcelStatus.PICKED_UP, null, "Call Nimal"),
                Actor.courier(courier));

        PublicTrackingView view = queries.findPublicTrackingView(created.trackingNumber().toLowerCase())
                .orElseThrow();

        assertThat(view.status()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(view.pickupCity()).isEqualTo("Colombo");
        assertThat(view.history()).extracting(event -> event.status())
                .containsExactly(ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
        assertThat(view.toString()).doesNotContain("Nimal", "+9477", "Temple", "ORD-P");
    }

    @Test
    void rebuiltEventsEqualTheOutboxPayloads() {
        CreatedParcel created = order("ORD-E");
        UUID courier = insertUser("courier");
        assignment.assignCourier(created.parcelId(), courier, dispatcherId);
        statusService.changeStatus(created.parcelId(), new StatusChange(ParcelStatus.PICKED_UP, null, null),
                Actor.courier(courier));

        List<ParcelStatusChanged> rebuilt = events.eventsBetween(created.parcelId(), 1, 5);
        List<ParcelStatusChanged> outbox = jdbc.queryForList(
                        "SELECT payload::text FROM parcel_outbox_events WHERE parcel_id = ? ORDER BY sequence",
                        String.class, created.parcelId()).stream()
                .map(serializer::fromJson)
                .toList();

        assertThat(rebuilt).hasSize(2).isEqualTo(outbox);
        assertThat(events.eventsBetween(created.parcelId(), 2, 2)).containsExactly(outbox.get(1));
    }

    private CreatedParcel order(String reference) {
        return creation.createMerchantParcel(new NewMerchantParcel(merchantId, reference, contact("Colombo"),
                contact("Kandy"), ParcelSize.SMALL, 1000, null));
    }

    private UUID insertUser(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, TRUE, now())",
                id, name + "-" + id + "@example.com", "Test " + name);
        return id;
    }

    private static DeliveryContact contact(String city) {
        return new DeliveryContact("Nimal Perera", "+94771234567", "12 Temple Rd", null, city, UUID.randomUUID(), null);
    }
}
