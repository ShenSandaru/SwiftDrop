package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.DispatchParcelSummary;
import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelHubOperations;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.Staff;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The hub lifecycle against real PostgreSQL (ADR 0009 §3, §7, ADR 0011 §2): a route between two
 * branches through the central hub or over a direct link, the attempt limit, the return, and the hub
 * code and attempt in history and outbox events.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
class HubLifecycleIT {

    private static final UUID GALLE_ZONE = UUID.fromString("00000000-0000-0000-0000-0000000096a1");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private ParcelBookingService booking;
    @Autowired
    private ParcelAssignment assignment;
    @Autowired
    private ParcelStatusService statuses;
    @Autowired
    private ParcelHubOperations hubOperations;
    @Autowired
    private DeliveryProofService proofs;
    @Autowired
    private HubAdministration hubAdministration;
    @Autowired
    private ParcelQueries queries;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private final JsonMapper json = JsonMapper.builder().build();
    private UUID customerId;
    private UUID courierId;
    private Staff dispatcher;

    @BeforeEach
    void setUp() {
        when(feeCalculator.calculateFee(any(), any(), any(), anyInt())).thenReturn(new FeeQuote(
                new BigDecimal("950.00"), "LKR", new BigDecimal("700.00"), new BigDecimal("150.00"),
                new BigDecimal("100.00"), UUID.randomUUID()));
        TestHubs.install(jdbc);
        // A second branch, so Galle to Kandy must pass through the Colombo hub
        jdbc.update("INSERT INTO hubs (id, code, name, city, zone_id, central, active, created_at, updated_at) "
                + "VALUES (gen_random_uuid(), 'GAL', 'Galle branch', 'Galle', ?, FALSE, TRUE, now(), now()) "
                + "ON CONFLICT (code) DO NOTHING", GALLE_ZONE);
        customerId = insertUser("CUSTOMER");
        courierId = insertUser("COURIER");
        UUID dispatcherId = insertUser("DISPATCHER");
        TestHubs.headOffice(jdbc, dispatcherId);
        dispatcher = Staff.dispatcher(dispatcherId);
    }

    @Test
    void branchToBranchTravelsThroughTheCentralHubAndEventsNameTheHubs() throws Exception {
        UUID parcel = bookGalleToKandy();
        pickUp(parcel);

        assertThat(jdbc.queryForObject("SELECT via_hub_id = (SELECT id FROM hubs WHERE code = 'CMB') FROM parcels "
                + "WHERE id = ?", Boolean.class, parcel)).as("route via the central hub").isTrue();
        TestHubs.toDestinationHub(hubOperations, jdbc, parcel, dispatcher);
        assignment.assignCourier(parcel, courierId, dispatcher.userId(), ParcelAssignmentType.DELIVERY);
        statuses.changeStatus(parcel, new StatusChange(ParcelStatus.OUT_FOR_DELIVERY, null, null),
                Actor.courier(courierId));

        assertThat(historyOf(parcel)).containsExactly(
                "CREATED", "PICKED_UP", "AT_HUB@GAL", "IN_TRANSIT@GAL", "AT_HUB@CMB", "IN_TRANSIT@CMB", "AT_HUB@KDY",
                "OUT_FOR_DELIVERY#1");
        JsonNode lastEvent = json.readTree(jdbc.queryForObject(
                "SELECT payload::text FROM parcel_outbox_events WHERE parcel_id = ? ORDER BY sequence DESC LIMIT 1",
                String.class, parcel));
        assertThat(lastEvent.get("data").get("attempt").asInt()).isEqualTo(1);
        JsonNode hubEvent = json.readTree(jdbc.queryForObject(
                "SELECT payload::text FROM parcel_outbox_events WHERE parcel_id = ? AND sequence = 5", String.class,
                parcel));
        assertThat(hubEvent.get("data").get("newStatus").asString()).isEqualTo("AT_HUB");
        assertThat(hubEvent.get("data").get("hubCode").asString()).isEqualTo("CMB");
    }

    @Test
    void linkedBranchesTravelDirectlyAndKeepTheirRouteWhenTheLinkIsRemoved() {
        UUID galle = jdbc.queryForObject("SELECT id FROM hubs WHERE code = 'GAL'", UUID.class);
        UUID kandy = jdbc.queryForObject("SELECT id FROM hubs WHERE code = 'KDY'", UUID.class);
        UUID admin = insertUser("ADMIN");
        UUID parcel = bookGalleToKandy();

        hubAdministration.link(galle, kandy, admin);
        try {
            // The pair is stored once, whichever order it is given in
            assertThatThrownBy(() -> hubAdministration.link(kandy, galle, admin))
                    .isInstanceOf(ParcelConflictException.class);
            assertThat(hubAdministration.links()).hasSize(1);
            pickUp(parcel);
        } finally {
            hubAdministration.unlink(kandy, galle);
        }

        assertThat(jdbc.queryForObject("SELECT via_hub_id IS NULL FROM parcels WHERE id = ?", Boolean.class, parcel))
                .as("route skips the central hub").isTrue();
        // The link is gone, but the route was fixed at pickup
        TestHubs.toDestinationHub(hubOperations, jdbc, parcel, dispatcher);
        assertThat(historyOf(parcel)).containsExactly(
                "CREATED", "PICKED_UP", "AT_HUB@GAL", "IN_TRANSIT@GAL", "AT_HUB@KDY");

        UUID later = bookGalleToKandy();
        pickUp(later);
        assertThat(jdbc.queryForObject("SELECT via_hub_id = (SELECT id FROM hubs WHERE code = 'CMB') FROM parcels "
                + "WHERE id = ?", Boolean.class, later)).as("a parcel picked up after the link was removed").isTrue();
    }

    @Test
    void publicTrackingEstimatesTheDeliveryDayFromTheRoute() {
        UUID parcel = bookGalleToKandy();
        String number = jdbc.queryForObject("SELECT tracking_number FROM parcels WHERE id = ?", String.class, parcel);
        LocalDate booked = jdbc.queryForObject(
                "SELECT (created_at AT TIME ZONE 'Asia/Colombo')::date FROM parcels WHERE id = ?", LocalDate.class,
                parcel);

        // Galle to Kandy passes through the central hub: three days by default
        assertThat(queries.findPublicTrackingView(number).orElseThrow().estimatedDelivery())
                .isEqualTo(booked.plusDays(3));

        hubOperations.cancel(parcel, dispatcher);
        assertThat(queries.findPublicTrackingView(number).orElseThrow().estimatedDelivery())
                .as("a finished parcel has no estimate").isNull();
    }

    @Test
    void parcelsWithNoRecentActivityAreStaleUntilTheyFinish() {
        UUID parcel = bookGalleToKandy();
        Instant anHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

        assertThat(staleIds(anHourAgo)).doesNotContain(parcel);

        jdbc.update("UPDATE parcels SET updated_at = now() - interval '2 days' WHERE id = ?", parcel);
        assertThat(staleIds(anHourAgo)).contains(parcel);
        assertThat(staleIds(Instant.now().minus(3, ChronoUnit.DAYS))).doesNotContain(parcel);

        hubOperations.cancel(parcel, dispatcher);
        jdbc.update("UPDATE parcels SET updated_at = now() - interval '2 days' WHERE id = ?", parcel);
        assertThat(staleIds(anHourAgo)).as("a finished parcel is never stale").doesNotContain(parcel);
    }

    private List<UUID> staleIds(Instant inactiveSince) {
        return queries.findStaleParcels(dispatcher.userId(), inactiveSince, new PageRequest(0, 100)).items().stream()
                .map(DispatchParcelSummary::id).toList();
    }

    @Test
    void threeFailedAttemptsLeadToTheReturn() {
        UUID parcel = bookGalleToKandy();
        pickUp(parcel);
        TestHubs.toDestinationHub(hubOperations, jdbc, parcel, dispatcher);
        UUID kandy = jdbc.queryForObject("SELECT id FROM hubs WHERE code = 'KDY'", UUID.class);

        for (int attempt = 1; attempt <= 3; attempt++) {
            assignment.assignCourier(parcel, courierId, dispatcher.userId(), ParcelAssignmentType.DELIVERY);
            statuses.changeStatus(parcel, new StatusChange(ParcelStatus.OUT_FOR_DELIVERY, null, null),
                    Actor.courier(courierId));
            proofs.capture(parcel, Actor.courier(courierId), "Someone", null);
            statuses.changeStatus(parcel, new StatusChange(ParcelStatus.DELIVERY_ATTEMPT_FAILED,
                    FailureReason.RECIPIENT_UNAVAILABLE, null), Actor.courier(courierId));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_proofs WHERE parcel_id = ?", Integer.class,
                    parcel)).as("proof of a failed attempt is discarded").isZero();
            if (attempt < 3) {
                assertThatThrownBy(() -> hubOperations.startReturn(parcel, dispatcher))
                        .isInstanceOf(ParcelConflictException.class);
            }
            hubOperations.scanAtHub(parcel, kandy, dispatcher);
        }

        assertThatThrownBy(() -> assignment.assignCourier(parcel, courierId, dispatcher.userId(),
                ParcelAssignmentType.DELIVERY)).isInstanceOf(ParcelConflictException.class);
        hubOperations.startReturn(parcel, dispatcher);
        assignment.assignCourier(parcel, courierId, dispatcher.userId(), ParcelAssignmentType.RETURN);
        proofs.capture(parcel, Actor.courier(courierId), "Nimal Perera", null);
        statuses.changeStatus(parcel, new StatusChange(ParcelStatus.RETURNED, null, null), Actor.courier(courierId));

        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcel))
                .isEqualTo("RETURNED");
        assertThat(jdbc.queryForObject("SELECT delivery_attempts FROM parcels WHERE id = ?", Integer.class, parcel))
                .isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT failure_reason_code FROM parcel_status_history WHERE parcel_id = ? "
                + "AND to_status = 'DELIVERY_ATTEMPT_FAILED'", String.class, parcel)).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parcel_assignments WHERE parcel_id = ? "
                + "AND status = 'ACTIVE'", Integer.class, parcel)).isZero();
    }

    @Test
    void dispatcherCancelsBeforePickupAndTheTaskIsCancelled() {
        UUID parcel = bookGalleToKandy();
        assignment.assignCourier(parcel, courierId, dispatcher.userId());

        hubOperations.cancel(parcel, dispatcher);

        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcel))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM parcel_assignments WHERE parcel_id = ?", String.class,
                parcel)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT actor_role FROM parcel_status_history WHERE parcel_id = ? "
                + "AND to_status = 'CANCELLED'", String.class, parcel)).isEqualTo("DISPATCHER");
    }

    private UUID bookGalleToKandy() {
        DeliveryContact galle = new DeliveryContact("Nimal Perera", "+94771234567", "4 Fort Rd", null, "Galle",
                GALLE_ZONE, null);
        DeliveryContact kandy = new DeliveryContact("Kumari Silva", "+94711234567", "4 Lake View", null, "Kandy",
                TestHubs.KANDY_ZONE, null);
        return booking.bookForCustomer(customerId, new Booking(galle, kandy, ParcelSize.SMALL, 1000, null)).getId();
    }

    private void pickUp(UUID parcel) {
        assignment.assignCourier(parcel, courierId, dispatcher.userId());
        statuses.changeStatus(parcel, new StatusChange(ParcelStatus.PICKED_UP, null, null), Actor.courier(courierId));
    }

    /** Each step as STATUS, with @HUB for hub steps and #ATTEMPT for delivery attempts. */
    private List<String> historyOf(UUID parcel) {
        return jdbc.query("SELECT to_status, hub_code, attempt FROM parcel_status_history WHERE parcel_id = ? "
                + "ORDER BY sequence", (row, i) -> row.getString("to_status")
                        + (row.getString("hub_code") != null ? "@" + row.getString("hub_code") : "")
                        + (row.getObject("attempt") != null ? "#" + row.getInt("attempt") : ""), parcel);
    }

    private UUID insertUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, TRUE, now())",
                id, role.toLowerCase() + "-" + id + "@example.com", "Test " + role.toLowerCase());
        jdbc.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, ?)", id, role);
        return id;
    }
}
