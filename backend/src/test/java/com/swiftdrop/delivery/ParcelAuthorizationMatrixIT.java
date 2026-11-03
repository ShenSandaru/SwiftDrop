package com.swiftdrop.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.Actor;
import com.swiftdrop.delivery.application.Booking;
import com.swiftdrop.delivery.application.DeliveryProofService;
import com.swiftdrop.delivery.application.ParcelBookingService;
import com.swiftdrop.delivery.application.ParcelStatusService;
import com.swiftdrop.delivery.application.StatusChange;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.test.security.AuthorizationMatrixExecutor;
import com.swiftdrop.test.security.AuthorizationMatrixRow;
import com.swiftdrop.test.security.TestPrincipal;

/**
 * Parcel authorization matrix (SD-316, TST-2): every delivery and tracking endpoint against every
 * kind of caller, on real data in real PostgreSQL. Not owned and not assigned is always 404, so IDs
 * cannot be probed; a role that never may use an endpoint is 403; no login is 401.
 *
 * <p>Rows that would change data send a deliberately invalid step: an allowed caller then gets 409
 * (past the security checks, stopped by the lifecycle rules), which keeps the matrix repeatable.
 * Successful changes are covered by {@code CourierFlowIT}.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@AutoConfigureMockMvc
@Testcontainers
class ParcelAuthorizationMatrixIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ParcelBookingService booking;
    @Autowired
    private ParcelAssignment assignment;
    @Autowired
    private ParcelStatusService statuses;
    @Autowired
    private DeliveryProofService proofs;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private TestPrincipal anonymous;
    private TestPrincipal owner;
    private TestPrincipal otherCustomer;
    private TestPrincipal assignedCourier;
    private TestPrincipal otherCourier;
    private TestPrincipal admin;
    private TestPrincipal merchant;
    /** Courier and customer at once, but neither owner nor assigned: two roles must not add access. */
    private TestPrincipal dualRole;

    /** Booked by the owner, assigned to the courier, still CREATED. */
    private Parcel booked;
    /** Delivered by the same courier, with a proof photo. */
    private Parcel delivered;

    @BeforeEach
    void createPeopleAndParcels() {
        when(feeCalculator.calculateFee(any(), any(), any(), anyInt())).thenReturn(new FeeQuote(
                new BigDecimal("650.00"), "LKR", new BigDecimal("400.00"), new BigDecimal("100.00"),
                new BigDecimal("150.00"), UUID.randomUUID()));
        anonymous = TestPrincipal.anonymous();
        UUID ownerId = insertUser(Role.CUSTOMER);
        owner = TestPrincipal.user(ownerId, Role.CUSTOMER);
        otherCustomer = TestPrincipal.user(insertUser(Role.CUSTOMER), Role.CUSTOMER);
        UUID courierId = insertUser(Role.COURIER);
        assignedCourier = TestPrincipal.user(courierId, Role.COURIER);
        otherCourier = TestPrincipal.user(insertUser(Role.COURIER), Role.COURIER);
        admin = TestPrincipal.user(insertUser(Role.DISPATCHER_ADMIN), Role.DISPATCHER_ADMIN);
        merchant = TestPrincipal.user(insertUser(Role.MERCHANT), Role.MERCHANT);
        dualRole = TestPrincipal.user(insertUser(Role.COURIER, Role.CUSTOMER), Role.COURIER, Role.CUSTOMER);

        booked = booking.bookForCustomer(ownerId, booking());
        assignment.assignCourier(booked.getId(), courierId, UUID.randomUUID());

        delivered = booking.bookForCustomer(ownerId, booking());
        assignment.assignCourier(delivered.getId(), courierId, UUID.randomUUID());
        for (ParcelStatus step : List.of(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY)) {
            statuses.changeStatus(delivered.getId(), new StatusChange(step, null, null), Actor.courier(courierId));
        }
        proofs.capture(delivered.getId(), Actor.courier(courierId), "Kumari Silva", JPEG);
        statuses.changeStatus(delivered.getId(), new StatusChange(ParcelStatus.DELIVERED, null, null),
                Actor.courier(courierId));
    }

    @TestFactory
    Stream<DynamicTest> parcelAuthorizationMatrix() {
        List<AuthorizationMatrixRow> rows = new ArrayList<>();
        String bookedPath = "/api/v1/parcels/" + booked.getId();
        String deliveredPath = "/api/v1/parcels/" + delivered.getId();

        // Book a delivery: customers only
        rows.add(json("book", HttpMethod.POST, "/api/v1/parcels", owner, HttpStatus.CREATED, bookingJson()));
        rows.add(json("book as courier+customer", HttpMethod.POST, "/api/v1/parcels", dualRole, HttpStatus.CREATED, bookingJson()));
        for (TestPrincipal other : List.of(assignedCourier, admin, merchant)) {
            rows.add(json("book", HttpMethod.POST, "/api/v1/parcels", other, HttpStatus.FORBIDDEN, bookingJson()));
        }
        rows.add(json("book", HttpMethod.POST, "/api/v1/parcels", anonymous, HttpStatus.UNAUTHORIZED, bookingJson()));

        // Delivery history: own for customers, all for admins
        rows.add(plain("list parcels", HttpMethod.GET, "/api/v1/parcels", owner, HttpStatus.OK));
        rows.add(plain("list parcels", HttpMethod.GET, "/api/v1/parcels", admin, HttpStatus.OK));
        rows.add(plain("list parcels", HttpMethod.GET, "/api/v1/parcels", assignedCourier, HttpStatus.FORBIDDEN));
        rows.add(plain("list parcels", HttpMethod.GET, "/api/v1/parcels", merchant, HttpStatus.FORBIDDEN));
        rows.add(plain("list parcels", HttpMethod.GET, "/api/v1/parcels", anonymous, HttpStatus.UNAUTHORIZED));

        // Parcel detail: owner, assigned courier, admin; strangers cannot tell it exists
        rows.add(plain("detail", HttpMethod.GET, bookedPath, owner, HttpStatus.OK));
        rows.add(plain("detail", HttpMethod.GET, bookedPath, assignedCourier, HttpStatus.OK));
        rows.add(plain("detail", HttpMethod.GET, bookedPath, admin, HttpStatus.OK));
        rows.add(plain("detail of someone else's parcel", HttpMethod.GET, bookedPath, otherCustomer, HttpStatus.NOT_FOUND));
        rows.add(plain("detail of another courier's parcel", HttpMethod.GET, bookedPath, otherCourier, HttpStatus.NOT_FOUND));
        rows.add(plain("detail of a parcel neither owned nor assigned", HttpMethod.GET, bookedPath, dualRole, HttpStatus.NOT_FOUND));
        rows.add(plain("detail", HttpMethod.GET, bookedPath, merchant, HttpStatus.FORBIDDEN));
        rows.add(plain("detail", HttpMethod.GET, bookedPath, anonymous, HttpStatus.UNAUTHORIZED));

        // Receipt: owner and admin
        rows.add(plain("receipt", HttpMethod.GET, bookedPath + "/receipt", owner, HttpStatus.OK));
        rows.add(plain("receipt", HttpMethod.GET, bookedPath + "/receipt", admin, HttpStatus.OK));
        rows.add(plain("receipt of someone else's parcel", HttpMethod.GET, bookedPath + "/receipt", otherCustomer, HttpStatus.NOT_FOUND));
        rows.add(plain("receipt", HttpMethod.GET, bookedPath + "/receipt", assignedCourier, HttpStatus.FORBIDDEN));
        rows.add(plain("receipt", HttpMethod.GET, bookedPath + "/receipt", merchant, HttpStatus.FORBIDDEN));
        rows.add(plain("receipt", HttpMethod.GET, bookedPath + "/receipt", anonymous, HttpStatus.UNAUTHORIZED));

        // Status change: only the assigned courier passes; an illegal step proves the rules ran (409)
        String illegalStep = "{\"toStatus\":\"DELIVERED\"}";
        rows.add(json("status change (illegal step)", HttpMethod.POST, bookedPath + "/status-changes", assignedCourier, HttpStatus.CONFLICT, illegalStep));
        rows.add(json("status change on another courier's parcel", HttpMethod.POST, bookedPath + "/status-changes", otherCourier, HttpStatus.NOT_FOUND, illegalStep));
        rows.add(json("status change on a parcel neither owned nor assigned", HttpMethod.POST, bookedPath + "/status-changes", dualRole, HttpStatus.NOT_FOUND, illegalStep));
        for (TestPrincipal other : List.of(owner, admin, merchant)) {
            rows.add(json("status change", HttpMethod.POST, bookedPath + "/status-changes", other, HttpStatus.FORBIDDEN, illegalStep));
        }
        rows.add(json("status change", HttpMethod.POST, bookedPath + "/status-changes", anonymous, HttpStatus.UNAUTHORIZED, illegalStep));

        // Proof upload: only the assigned courier passes; before "out for delivery" that is a 409
        rows.add(proof("proof before out for delivery", bookedPath, assignedCourier, HttpStatus.CONFLICT));
        rows.add(proof("proof for another courier's parcel", bookedPath, otherCourier, HttpStatus.NOT_FOUND));
        for (TestPrincipal other : List.of(owner, admin, merchant)) {
            rows.add(proof("proof", bookedPath, other, HttpStatus.FORBIDDEN));
        }
        rows.add(proof("proof", bookedPath, anonymous, HttpStatus.UNAUTHORIZED));

        // Proof photo: owner, assigned courier, admin
        String photo = deliveredPath + "/proof/photo";
        rows.add(plain("proof photo", HttpMethod.GET, photo, owner, HttpStatus.OK));
        rows.add(plain("proof photo", HttpMethod.GET, photo, assignedCourier, HttpStatus.OK));
        rows.add(plain("proof photo", HttpMethod.GET, photo, admin, HttpStatus.OK));
        rows.add(plain("proof photo of someone else's parcel", HttpMethod.GET, photo, otherCustomer, HttpStatus.NOT_FOUND));
        rows.add(plain("proof photo of another courier's parcel", HttpMethod.GET, photo, otherCourier, HttpStatus.NOT_FOUND));
        rows.add(plain("proof photo", HttpMethod.GET, photo, merchant, HttpStatus.FORBIDDEN));
        rows.add(plain("proof photo", HttpMethod.GET, photo, anonymous, HttpStatus.UNAUTHORIZED));

        // Courier run: couriers only (contents checked separately below)
        rows.add(plain("courier run", HttpMethod.GET, "/api/v1/courier/parcels", assignedCourier, HttpStatus.OK));
        rows.add(plain("courier run", HttpMethod.GET, "/api/v1/courier/parcels", otherCourier, HttpStatus.OK));
        for (TestPrincipal other : List.of(owner, admin, merchant)) {
            rows.add(plain("courier run", HttpMethod.GET, "/api/v1/courier/parcels", other, HttpStatus.FORBIDDEN));
        }
        rows.add(plain("courier run", HttpMethod.GET, "/api/v1/courier/parcels", anonymous, HttpStatus.UNAUTHORIZED));

        // Public tracking: everyone may read, nobody may write
        String tracked = "/api/v1/tracked-parcels/" + delivered.getTrackingNumber();
        for (TestPrincipal anyone : List.of(anonymous, owner, otherCustomer, otherCourier, merchant)) {
            rows.add(plain("public tracking", HttpMethod.GET, tracked, anyone, HttpStatus.OK));
        }
        rows.add(plain("public tracking stream", HttpMethod.GET, tracked + "/events", anonymous, HttpStatus.OK));
        rows.add(plain("public tracking of an unknown number", HttpMethod.GET, "/api/v1/tracked-parcels/SD0000000000",
                anonymous, HttpStatus.NOT_FOUND));
        rows.add(plain("write to public tracking", HttpMethod.POST, tracked, anonymous, HttpStatus.UNAUTHORIZED));

        return AuthorizationMatrixExecutor.toDynamicTests(mvc, rows);
    }

    @Test
    void couriersOnlySeeTheirOwnRun() throws Exception {
        String own = mvc.perform(get("/api/v1/courier/parcels").with(assignedCourier.postProcessor()))
                .andReturn().getResponse().getContentAsString();
        String other = mvc.perform(get("/api/v1/courier/parcels").with(otherCourier.postProcessor()))
                .andReturn().getResponse().getContentAsString();

        assertThat(own).contains(booked.getTrackingNumber(), delivered.getTrackingNumber());
        assertThat(other).isEqualTo("[]");
    }

    @Test
    void customersOnlyListTheirOwnParcels() throws Exception {
        String own = mvc.perform(get("/api/v1/parcels").with(owner.postProcessor()))
                .andReturn().getResponse().getContentAsString();
        String other = mvc.perform(get("/api/v1/parcels").with(otherCustomer.postProcessor()))
                .andReturn().getResponse().getContentAsString();

        assertThat(own).contains(booked.getTrackingNumber(), delivered.getTrackingNumber());
        assertThat(other).doesNotContain(booked.getTrackingNumber(), delivered.getTrackingNumber());
    }

    private static AuthorizationMatrixRow plain(String description, HttpMethod method, String path, TestPrincipal principal,
            HttpStatus expected) {
        return AuthorizationMatrixRow.row(description, method, path, principal, expected);
    }

    private static AuthorizationMatrixRow json(String description, HttpMethod method, String path, TestPrincipal principal,
            HttpStatus expected, String body) {
        return new AuthorizationMatrixRow(description, method, path, principal, expected,
                request -> request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** A valid multipart proof upload, so only authorization and lifecycle decide the answer. */
    private static AuthorizationMatrixRow proof(String description, String parcelPath, TestPrincipal principal,
            HttpStatus expected) {
        return new AuthorizationMatrixRow(description, HttpMethod.PUT, parcelPath + "/proof", principal, expected,
                (MockHttpServletRequestBuilder request) -> request.contentType(MediaType.MULTIPART_FORM_DATA)
                        .param("recipientName", "Kumari Silva"));
    }

    private UUID insertUser(Role... roles) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, 'Matrix user', TRUE, now())",
                id, "matrix-" + id + "@example.com");
        for (Role role : roles) {
            jdbc.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, ?)", id, role.name());
        }
        return id;
    }

    private static Booking booking() {
        DeliveryContact contact = new DeliveryContact("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null);
        return new Booking(contact, contact, ParcelSize.SMALL, 1000, null);
    }

    private static String bookingJson() {
        String address = "{\"contactName\":\"Nimal Perera\",\"phone\":\"+94771234567\",\"line1\":\"12 Temple Rd\","
                + "\"city\":\"Colombo\",\"zoneId\":\"" + UUID.randomUUID() + "\"}";
        return "{\"pickup\":" + address + ",\"dropoff\":" + address + ",\"size\":\"SMALL\",\"weightGrams\":1000}";
    }
}
