package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import com.swiftdrop.delivery.api.DispatchAction;
import com.swiftdrop.delivery.api.DispatchParcelSummary;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelForbiddenException;
import com.swiftdrop.delivery.api.ParcelHubOperations;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.Staff;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * Hub-scoped staff against real PostgreSQL (ADR 0012): one parcel travels Galle, Colombo, Kandy while
 * dispatchers and couriers of each hub, an admin and a dispatcher without a hub try to see it, act
 * on it and assign couriers.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
class HubScopeIT {

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
    private ParcelQueries queries;
    @Autowired
    private HubStaffAdministration staffAdministration;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private UUID admin;
    private UUID customer;
    private UUID galleDispatcher;
    private UUID colomboDispatcher;
    private UUID kandyDispatcher;
    private UUID realAdmin;
    private UUID noHubDispatcher;
    private UUID galleCourier;
    private UUID kandyCourier;
    private UUID galle;
    private UUID colombo;
    private UUID kandy;

    @BeforeEach
    void setUp() {
        when(feeCalculator.calculateFee(any(), any(), any(), anyInt())).thenReturn(new FeeQuote(
                new BigDecimal("950.00"), "LKR", new BigDecimal("700.00"), new BigDecimal("150.00"),
                new BigDecimal("100.00"), UUID.randomUUID()));
        TestHubs.install(jdbc);
        jdbc.update("INSERT INTO hubs (id, code, name, city, zone_id, central, active, created_at, updated_at) "
                + "VALUES (gen_random_uuid(), 'GAL', 'Galle branch', 'Galle', ?, FALSE, TRUE, now(), now()) "
                + "ON CONFLICT (code) DO NOTHING", GALLE_ZONE);
        galle = hubId("GAL");
        colombo = hubId("CMB");
        kandy = hubId("KDY");

        // Only one admin account may exist, and created_by has no foreign key (ADR 0003)
        admin = UUID.randomUUID();
        customer = insertUser("CUSTOMER");
        galleDispatcher = insertUser("DISPATCHER");
        colomboDispatcher = insertUser("DISPATCHER");
        kandyDispatcher = insertUser("DISPATCHER");
        realAdmin = TestHubs.adminUser(jdbc);
        noHubDispatcher = insertUser("DISPATCHER");
        galleCourier = insertUser("COURIER");
        kandyCourier = insertUser("COURIER");

        // Through the admin service, as the admin page does
        staffAdministration.addToHub(galle, galleDispatcher, admin);
        staffAdministration.addToHub(colombo, colomboDispatcher, admin);
        staffAdministration.addToHub(kandy, kandyDispatcher, admin);
        staffAdministration.addToHub(galle, galleCourier, admin);
        staffAdministration.addToHub(kandy, kandyCourier, admin);
    }

    @Test
    void aParcelIsHandedFromHubToHubAndEachDispatcherSeesAndActsOnlyWhereTheyShould() {
        UUID parcel = bookGalleToKandy();

        // Before pickup it belongs to the pickup branch
        assertThat(visibleTo(galleDispatcher, parcel)).isNotNull().satisfies(p -> assertThat(p.readOnly()).isFalse());
        assertThat(visibleTo(colomboDispatcher, parcel)).isNull();
        assertThat(visibleTo(kandyDispatcher, parcel)).isNull();
        assertThat(visibleTo(noHubDispatcher, parcel)).as("no hub sees nothing").isNull();
        assertThat(visibleTo(realAdmin, parcel)).as("an admin sees every parcel").isNotNull();

        // Only the pickup branch may assign, and only a courier of that branch
        assertThatThrownBy(() -> assignment.assignCourier(parcel, galleCourier, kandyDispatcher))
                .isInstanceOf(ParcelForbiddenException.class);
        assertThatThrownBy(() -> assignment.assignCourier(parcel, galleCourier, noHubDispatcher))
                .isInstanceOf(ParcelForbiddenException.class);
        assertThatThrownBy(() -> assignment.assignCourier(parcel, kandyCourier, galleDispatcher))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("The courier does not work for this parcel's hub");
        assignment.assignCourier(parcel, galleCourier, galleDispatcher);
        statuses.changeStatus(parcel, new StatusChange(ParcelStatus.PICKED_UP, null, null),
                Actor.courier(galleCourier));

        // At Galle: only Galle scans it in
        assertThatThrownBy(() -> hubOperations.scanAtHub(parcel, galle, Staff.dispatcher(kandyDispatcher)))
                .isInstanceOf(ParcelForbiddenException.class);
        hubOperations.scanAtHub(parcel, galle, Staff.dispatcher(galleDispatcher));
        hubOperations.sendToNextHub(parcel, Staff.dispatcher(galleDispatcher));

        // On the truck to Colombo: Colombo acts, Galle and Kandy only follow it
        assertThat(visibleTo(colomboDispatcher, parcel).readOnly()).isFalse();
        assertThat(visibleTo(colomboDispatcher, parcel).actions()).contains(DispatchAction.SCAN_AT_HUB);
        assertThat(visibleTo(galleDispatcher, parcel).readOnly()).isTrue();
        assertThat(visibleTo(galleDispatcher, parcel).actions()).isEmpty();
        assertThat(visibleTo(kandyDispatcher, parcel).readOnly()).isTrue();
        assertThatThrownBy(() -> hubOperations.sendToNextHub(parcel, Staff.dispatcher(galleDispatcher)))
                .isInstanceOf(ParcelForbiddenException.class);

        hubOperations.scanAtHub(parcel, colombo, Staff.dispatcher(colomboDispatcher));
        hubOperations.sendToNextHub(parcel, Staff.dispatcher(colomboDispatcher));

        // On the truck to Kandy
        assertThat(visibleTo(kandyDispatcher, parcel).readOnly()).isFalse();
        assertThat(visibleTo(colomboDispatcher, parcel).readOnly()).isTrue();
        hubOperations.scanAtHub(parcel, kandy, Staff.dispatcher(kandyDispatcher));

        // The delivery goes to a Kandy courier, not the Galle one
        assertThatThrownBy(() -> assignment.assignCourier(parcel, galleCourier, kandyDispatcher,
                ParcelAssignmentType.DELIVERY)).isInstanceOf(ParcelConflictException.class);
        assertThatThrownBy(() -> assignment.assignCourier(parcel, kandyCourier, galleDispatcher,
                ParcelAssignmentType.DELIVERY)).isInstanceOf(ParcelForbiddenException.class);
        assignment.assignCourier(parcel, kandyCourier, kandyDispatcher, ParcelAssignmentType.DELIVERY);
        statuses.changeStatus(parcel, new StatusChange(ParcelStatus.OUT_FOR_DELIVERY, null, null),
                Actor.courier(kandyCourier));

        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcel))
                .isEqualTo("OUT_FOR_DELIVERY");
        // Everyone on the route can still follow it, the others cannot
        assertThat(visibleTo(galleDispatcher, parcel)).isNotNull();
        assertThat(visibleTo(noHubDispatcher, parcel)).isNull();
    }

    @Test
    void courierListsAndStaleListsFollowTheCallersHubs() {
        UUID parcel = bookGalleToKandy();
        jdbc.update("UPDATE parcels SET updated_at = now() - interval '3 days' WHERE id = ?", parcel);
        java.time.Instant cutoff = java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS);

        assertThat(staleIds(galleDispatcher, cutoff)).contains(parcel);
        assertThat(staleIds(kandyDispatcher, cutoff)).doesNotContain(parcel);
        assertThat(staleIds(noHubDispatcher, cutoff)).isEmpty();
        assertThat(staleIds(realAdmin, cutoff)).contains(parcel);
    }

    @Test
    void anAdminChangeMovesWhatTheStaffMemberSees() {
        UUID parcel = bookGalleToKandy();
        assertThat(visibleTo(galleDispatcher, parcel)).isNotNull();

        staffAdministration.removeFromHub(galle, galleDispatcher);
        assertThat(visibleTo(galleDispatcher, parcel)).as("no hub left").isNull();

        staffAdministration.addToHub(kandy, galleDispatcher, admin);
        assertThat(visibleTo(galleDispatcher, parcel)).as("before pickup only the pickup branch sees it").isNull();

        staffAdministration.addToHub(galle, galleDispatcher, admin);
        assertThat(visibleTo(galleDispatcher, parcel).readOnly()).as("back at the pickup branch").isFalse();
    }

    @Test
    void aCourierWithNoHubCanOnlyBeAssignedByAnAdmin() {
        UUID freeCourier = insertUser("COURIER");
        UUID parcel = bookGalleToKandy();

        assertThatThrownBy(() -> assignment.assignCourier(parcel, freeCourier, galleDispatcher))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("The courier is not assigned to a hub");
        assignment.assignCourier(parcel, freeCourier, realAdmin);

        assertThat(jdbc.queryForObject("SELECT assigned_courier_id FROM parcels WHERE id = ?", UUID.class, parcel))
                .isEqualTo(freeCourier);
    }

    @Test
    void theCustomerStillCancelsWithoutAnyHub() {
        UUID parcel = bookGalleToKandy();

        assertThatThrownBy(() -> hubOperations.cancel(parcel, Staff.dispatcher(kandyDispatcher)))
                .isInstanceOf(ParcelForbiddenException.class);
        hubOperations.cancel(parcel, Staff.dispatcher(galleDispatcher));

        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcel))
                .isEqualTo("CANCELLED");
    }

    private DispatchParcelSummary visibleTo(UUID staffUserId, UUID parcelId) {
        return queries.findDispatchParcels(staffUserId, null, null, null, null, new PageRequest(0, 100)).items()
                .stream().filter(parcel -> parcel.id().equals(parcelId)).findFirst().orElse(null);
    }

    private java.util.List<UUID> staleIds(UUID staffUserId, java.time.Instant cutoff) {
        return queries.findStaleParcels(staffUserId, cutoff, new PageRequest(0, 100)).items().stream()
                .map(DispatchParcelSummary::id).toList();
    }

    private UUID bookGalleToKandy() {
        DeliveryContact from = new DeliveryContact("Nimal Perera", "+94771234567", "4 Fort Rd", null, "Galle",
                GALLE_ZONE, null);
        DeliveryContact to = new DeliveryContact("Kumari Silva", "+94711234567", "4 Lake View", null, "Kandy",
                TestHubs.KANDY_ZONE, null);
        return booking.bookForCustomer(customer, new Booking(from, to, ParcelSize.SMALL, 1000, null)).getId();
    }

    private UUID hubId(String code) {
        return jdbc.queryForObject("SELECT id FROM hubs WHERE code = ?", UUID.class, code);
    }

    private UUID insertUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, TRUE, now())",
                id, role.toLowerCase() + "-" + id + "@example.com", "Test " + role.toLowerCase());
        jdbc.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, ?)", id, role);
        return id;
    }
}
