package com.swiftdrop.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
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
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.Booking;
import com.swiftdrop.delivery.application.ParcelBookingService;
import com.swiftdrop.delivery.application.ParcelReadService;
import com.swiftdrop.delivery.application.ParcelView;
import com.swiftdrop.delivery.application.Viewer;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryEntry;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.test.security.TestPrincipal;

/** The parcel endpoints over HTTP: roles, validation, response shapes and 404 for other people's parcels. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class ParcelApiTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID COURIER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID ZONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ParcelBookingService booking;
    @MockitoBean
    private ParcelReadService reads;

    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private ExternalIdentityRepository identities;
    @MockitoBean
    private GoogleLoginCodeRepository googleCodes;
    @MockitoBean
    private ParcelRepository parcels;
    @MockitoBean
    private ParcelStatusHistoryRepository parcelHistory;
    @MockitoBean
    private DeliveryProofRepository deliveryProofs;
    @MockitoBean
    private OutboxEventRepository outboxEvents;
    @MockitoBean
    private FeeCalculator feeCalculator;

    private Parcel parcel;

    @BeforeEach
    void setUp() {
        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                ZONE, "00500");
        parcel = Parcel.forCustomer("SD7K3M9Q2XWB", CUSTOMER_ID, address, address, ParcelSize.MEDIUM, 2000, "Books",
                new Parcel.Fee(new BigDecimal("850.00"), "LKR", UUID.randomUUID()), NOW);
        ReflectionTestUtils.setField(parcel, "id", PARCEL_ID);
    }

    @Test
    void customerBooksAndGets201WithLocationTrackingNumberAndServerFee() throws Exception {
        when(booking.bookForCustomer(eq(CUSTOMER_ID), any())).thenReturn(parcel);

        mvc.perform(post("/api/v1/parcels").with(TestPrincipal.customer(CUSTOMER_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        // A client-supplied fee and owner are ignored: the record has no such fields
                        .content(bookingJson("+94771234567", 2000).replace("{\"pickup\"",
                                "{\"fee\":{\"amount\":\"1.00\"},\"customerId\":\"" + UUID.randomUUID() + "\",\"pickup\"")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/parcels/" + PARCEL_ID)))
                .andExpect(jsonPath("$.trackingNumber").value("SD7K3M9Q2XWB"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.fee.amount").value("850.00"))
                .andExpect(jsonPath("$.fee.currency").value("LKR"));

        ArgumentCaptor<Booking> sent = ArgumentCaptor.forClass(Booking.class);
        verify(booking).bookForCustomer(eq(CUSTOMER_ID), sent.capture());
        assertThat(sent.getValue().weightGrams()).isEqualTo(2000);
    }

    @Test
    void invalidBookingListsFieldPathsWithoutValues() throws Exception {
        mvc.perform(post("/api/v1/parcels").with(TestPrincipal.customer(CUSTOMER_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson("0771234567", 30_001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("dropoff.phone", "pickup.phone", "weightGrams")))
                .andExpect(jsonPath("$.detail").value("Invalid request"));
        verify(booking, never()).bookForCustomer(any(), any());
    }

    @Test
    void onlyCustomersMayBookAndAnonymousIsRejected() throws Exception {
        for (TestPrincipal other : List.of(TestPrincipal.courier(), TestPrincipal.merchant(),
                TestPrincipal.dispatcherAdmin())) {
            mvc.perform(post("/api/v1/parcels").with(other.postProcessor())
                            .contentType(MediaType.APPLICATION_JSON).content(bookingJson("+94771234567", 2000)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/parcels").contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson("+94771234567", 2000)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsPageShapeWithCitiesOnly() throws Exception {
        when(reads.list(any(), eq(null), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(parcel), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/parcels").with(TestPrincipal.customer(CUSTOMER_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].trackingNumber").value("SD7K3M9Q2XWB"))
                .andExpect(jsonPath("$.items[0].pickupCity").value("Colombo"))
                .andExpect(jsonPath("$.items[0].pickup").doesNotExist())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        ArgumentCaptor<Viewer> viewer = ArgumentCaptor.forClass(Viewer.class);
        verify(reads).list(viewer.capture(), eq(null), eq(0), eq(20));
        assertThat(viewer.getValue())
                .isEqualTo(Viewer.of(CUSTOMER_ID, ActorRole.CUSTOMER));
    }

    @Test
    void listClampsPageSizeAndRejectsUnknownStatus() throws Exception {
        when(reads.list(any(), any(), eq(0), eq(100))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/parcels?size=500").with(TestPrincipal.dispatcherAdmin().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get("/api/v1/parcels?status=LOST").with(TestPrincipal.customer(CUSTOMER_ID).postProcessor()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("status")));
    }

    @Test
    void couriersAndMerchantsCannotListParcels() throws Exception {
        mvc.perform(get("/api/v1/parcels").with(TestPrincipal.courier().postProcessor()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/parcels").with(TestPrincipal.merchant().postProcessor()))
                .andExpect(status().isForbidden());
    }

    @Test
    void detailShowsAddressesAndTimelineWithNotesOnlyWhenAllowed() throws Exception {
        ParcelStatusHistoryEntry created = new ParcelStatusHistoryEntry(PARCEL_ID, 1, null, ParcelStatus.CREATED,
                CUSTOMER_ID, ActorRole.CUSTOMER, "Ring twice", null, UUID.randomUUID(), NOW);
        when(reads.detail(eq(PARCEL_ID), any())).thenReturn(new ParcelView(parcel, List.of(created), false, null));

        mvc.perform(get("/api/v1/parcels/" + PARCEL_ID).with(TestPrincipal.customer(CUSTOMER_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickup.phone").value("+94771234567"))
                .andExpect(jsonPath("$.history[0].status").value("CREATED"))
                .andExpect(jsonPath("$.history[0].note").value(nullValue()));

        when(reads.detail(eq(PARCEL_ID), any())).thenReturn(new ParcelView(parcel, List.of(created), true, null));
        mvc.perform(get("/api/v1/parcels/" + PARCEL_ID).with(TestPrincipal.courier(COURIER_ID).postProcessor()))
                .andExpect(jsonPath("$.history[0].note").value("Ring twice"));
    }

    @Test
    void someoneElsesParcelAndBadIdsAreNotFoundOrBadRequest() throws Exception {
        when(reads.detail(eq(PARCEL_ID), any())).thenThrow(new ParcelNotFoundException());

        mvc.perform(get("/api/v1/parcels/" + PARCEL_ID).with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Parcel not found"));
        mvc.perform(get("/api/v1/parcels/not-a-uuid").with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void receiptOfFailedParcelShowsFailureAndNoDelivery() throws Exception {
        ReflectionTestUtils.setField(parcel, "status", ParcelStatus.FAILED);
        ParcelStatusHistoryEntry failed = new ParcelStatusHistoryEntry(PARCEL_ID, 3, ParcelStatus.PICKED_UP,
                ParcelStatus.FAILED, COURIER_ID, ActorRole.COURIER, null, FailureReason.DAMAGED, UUID.randomUUID(),
                NOW.plusSeconds(600));
        when(reads.receipt(eq(PARCEL_ID), any())).thenReturn(new ParcelView(parcel, List.of(failed), false, null));

        mvc.perform(get("/api/v1/parcels/" + PARCEL_ID + "/receipt")
                        .with(TestPrincipal.customer(CUSTOMER_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("SD7K3M9Q2XWB"))
                .andExpect(jsonPath("$.fee.amount").value("850.00"))
                .andExpect(jsonPath("$.failureReason").value("DAMAGED"))
                .andExpect(jsonPath("$.failedAt").value("2026-11-03T10:25:30Z"))
                .andExpect(jsonPath("$.deliveredAt").value(nullValue()))
                .andExpect(jsonPath("$.recipientName").value(nullValue()));
    }

    @Test
    void couriersCannotOpenReceipts() throws Exception {
        mvc.perform(get("/api/v1/parcels/" + PARCEL_ID + "/receipt").with(TestPrincipal.courier().postProcessor()))
                .andExpect(status().isForbidden());
    }

    private static String bookingJson(String phone, int weightGrams) {
        String address = "{\"contactName\":\"Nimal Perera\",\"phone\":\"" + phone + "\",\"line1\":\"12 Temple Rd\","
                + "\"city\":\"Colombo\",\"zoneId\":\"" + ZONE + "\"}";
        return "{\"pickup\":" + address + ",\"dropoff\":" + address + ",\"size\":\"MEDIUM\",\"weightGrams\":"
                + weightGrams + ",\"description\":\"Books\"}";
    }
}
