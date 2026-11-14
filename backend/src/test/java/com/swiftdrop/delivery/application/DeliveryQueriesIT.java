package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
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

import com.swiftdrop.delivery.TestHubs;
import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.DispatchActivity;
import com.swiftdrop.delivery.api.DispatchActivityAction;
import com.swiftdrop.delivery.api.DispatchActivityFilter;
import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.delivery.api.ParcelHubOperations;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.delivery.api.PublicTrackingView;
import com.swiftdrop.delivery.api.Staff;
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
    private ParcelHubOperations hubOperations;
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
        // Booking needs served zones (ADR 0009 §1)
        TestHubs.install(jdbc);
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
    void dispatcherActivityHistoryIncludesAssignmentAndStatusActionsAndCorrectReassignmentActor() {
        CreatedParcel pickupParcel = order("ORD-H-PICKUP");
        UUID firstCourier = insertUser("courier");
        UUID replacementCourier = insertUser("courier");
        UUID replacementDispatcher = insertUser("dispatcher-reassignment");
        assignment.assignCourier(pickupParcel.parcelId(), firstCourier, dispatcherId, ParcelAssignmentType.PICKUP);
        assignment.assignCourier(
                pickupParcel.parcelId(), replacementCourier, replacementDispatcher, ParcelAssignmentType.PICKUP);

        CreatedParcel deliveryParcel = order("ORD-H-DELIVERY");
        insertAssignment(deliveryParcel.parcelId(), insertUser("courier"), ParcelAssignmentType.DELIVERY, dispatcherId);
        CreatedParcel returnParcel = order("ORD-H-RETURN");
        insertAssignment(returnParcel.parcelId(), insertUser("courier"), ParcelAssignmentType.RETURN, null);

        CreatedParcel statusParcel = order("ORD-H-STATUS");
        Instant base = Instant.parse("2026-11-12T10:00:00Z");
        insertStatus(statusParcel.parcelId(), 2, "PICKED_UP", "AT_HUB", dispatcherId, "DISPATCHER",
                base, "CMB", null, null);
        insertStatus(statusParcel.parcelId(), 3, "AT_HUB", "IN_TRANSIT", dispatcherId, "ADMIN",
                base.plusSeconds(1), "CMB", null, null);
        insertStatus(statusParcel.parcelId(), 4, "IN_TRANSIT", "FAILED", dispatcherId, "DISPATCHER",
                base.plusSeconds(2), null, null, "DAMAGED");
        UUID courierId = insertUser("courier");
        insertStatus(statusParcel.parcelId(), 5, "OUT_FOR_DELIVERY", "DELIVERY_ATTEMPT_FAILED", courierId,
                "COURIER", base.plusSeconds(3), null, 1, "REFUSED");
        insertStatus(statusParcel.parcelId(), 6, "AT_HUB", "RETURNING", dispatcherId, "DISPATCHER",
                base.plusSeconds(4), null, null, null);
        insertStatus(statusParcel.parcelId(), 7, "CREATED", "CANCELLED", dispatcherId, "ADMIN",
                base.plusSeconds(5), null, null, null);

        List<DispatchActivity> activities = queries.findDispatchActivityHistory(
                new DispatchActivityFilter(null, null, null, null, null), new PageRequest(0, 100)).items();

        assertThat(activities).extracting(DispatchActivity::actionType)
                .contains(
                        DispatchActivityAction.PICKUP_ASSIGNED,
                        DispatchActivityAction.DELIVERY_ASSIGNED,
                        DispatchActivityAction.RETURN_ASSIGNED,
                        DispatchActivityAction.COURIER_REASSIGNED,
                        DispatchActivityAction.HUB_SCAN,
                        DispatchActivityAction.TRANSIT,
                        DispatchActivityAction.FAILURE,
                        DispatchActivityAction.RETURN_INITIATED,
                        DispatchActivityAction.CANCELLATION);
        assertThat(activities).noneMatch(
                activity -> activity.details().toStatus() == ParcelStatus.DELIVERY_ATTEMPT_FAILED);
        DispatchActivity reassignment = activities.stream()
                .filter(activity -> activity.actionType() == DispatchActivityAction.COURIER_REASSIGNED)
                .findFirst()
                .orElseThrow();
        assertThat(reassignment.actorId()).isEqualTo(replacementDispatcher);
        assertThat(reassignment.details().courierId()).isEqualTo(replacementCourier);

        DispatchActivity legacyAssignment = activities.stream()
                .filter(activity -> activity.trackingNumber().equals(returnParcel.trackingNumber()))
                .findFirst()
                .orElseThrow();
        assertThat(legacyAssignment.actorId()).isNull();
    }

    @Test
    void dispatcherActivityHistoryFiltersSortsAndPaginatesAcrossHistoryRows() {
        CreatedParcel parcel = order("ORD-H-FILTER");
        CreatedParcel otherParcel = order("ORD-H-OTHER");
        Instant older = Instant.parse("2026-11-12T10:00:00Z");
        Instant newer = older.plusSeconds(60);
        insertStatus(parcel.parcelId(), 2, "PICKED_UP", "AT_HUB", dispatcherId, "DISPATCHER",
                older, "CMB", null, null);
        insertStatus(parcel.parcelId(), 3, "AT_HUB", "IN_TRANSIT", dispatcherId, "DISPATCHER",
                newer, "CMB", null, null);
        UUID otherDispatcher = insertUser("another-dispatcher");
        insertStatus(otherParcel.parcelId(), 2, "CREATED", "CANCELLED", otherDispatcher, "ADMIN",
                newer.plusSeconds(1), null, null, null);

        PageResult<DispatchActivity> firstPage = queries.findDispatchActivityHistory(
                new DispatchActivityFilter(null, null, null, parcel.trackingNumber(), dispatcherId),
                new PageRequest(0, 1));
        PageResult<DispatchActivity> secondPage = queries.findDispatchActivityHistory(
                new DispatchActivityFilter(null, null, null, parcel.trackingNumber(), dispatcherId),
                new PageRequest(1, 1));
        PageResult<DispatchActivity> dateAndActionFiltered = queries.findDispatchActivityHistory(
                new DispatchActivityFilter(older, newer, DispatchActivityAction.HUB_SCAN,
                        parcel.trackingNumber().toLowerCase(), dispatcherId),
                new PageRequest(0, 20));

        assertThat(firstPage.items()).extracting(DispatchActivity::actionType)
                .containsExactly(DispatchActivityAction.TRANSIT);
        assertThat(firstPage.totalItems()).isEqualTo(2);
        assertThat(secondPage.items()).extracting(DispatchActivity::actionType)
                .containsExactly(DispatchActivityAction.HUB_SCAN);
        assertThat(dateAndActionFiltered.items()).extracting(DispatchActivity::actionType)
                .containsExactly(DispatchActivityAction.HUB_SCAN);
        assertThat(dateAndActionFiltered.items().getFirst().occurredAt()).isEqualTo(older);
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
        // Picked up, the courier's task ends (ADR 0009 §5); a dispatcher marks the damaged parcel failed
        hubOperations.markFailed(failing, FailureReason.DAMAGED, null, Staff.dispatcher(dispatcherId));

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

        Comparator<Instant> withinPostgresRounding = (t1, t2) ->
                Math.abs(ChronoUnit.NANOS.between(t1, t2)) <= 1000 ? 0 : t1.compareTo(t2);

        assertThat(rebuilt).hasSize(2)
                .usingRecursiveComparison()
                .withComparatorForType(withinPostgresRounding, Instant.class)
                .isEqualTo(outbox);
        assertThat(events.eventsBetween(created.parcelId(), 2, 2))
                .usingRecursiveComparison()
                .withComparatorForType(withinPostgresRounding, Instant.class)
                .isEqualTo(List.of(outbox.get(1)));
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

    private void insertAssignment(UUID parcelId, UUID courierId, ParcelAssignmentType type, UUID assignedBy) {
        jdbc.update("""
                INSERT INTO parcel_assignments (
                    id, parcel_id, courier_id, assignment_type, status, assigned_at, completed_at, dispatcher_id
                ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, NULL, ?)
                """, UUID.randomUUID(), parcelId, courierId, type.name(),
                OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC), assignedBy);
    }

    private void insertStatus(UUID parcelId, int sequence, String fromStatus, String toStatus, UUID actorId,
            String actorRole, Instant occurredAt, String hubCode, Integer attempt, String failureReason) {
        jdbc.update("""
                INSERT INTO parcel_status_history (
                    id, parcel_id, sequence, from_status, to_status, actor_user_id, actor_role,
                    event_id, occurred_at, hub_code, attempt, failure_reason_code
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), parcelId, sequence, fromStatus, toStatus, actorId, actorRole,
                UUID.randomUUID(), OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC), hubCode, attempt, failureReason);
    }

    private static DeliveryContact contact(String city) {
        return new DeliveryContact("Nimal Perera", "+94771234567", "12 Temple Rd", null, city, TestHubs.zoneFor(city),
                null);
    }
}
