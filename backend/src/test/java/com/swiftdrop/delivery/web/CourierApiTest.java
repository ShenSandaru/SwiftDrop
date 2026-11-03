package com.swiftdrop.delivery.web;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.CourierParcelService;
import com.swiftdrop.delivery.application.CourierParcelService.CourierParcel;
import com.swiftdrop.delivery.application.DeliveryProofService;
import com.swiftdrop.delivery.application.ParcelReadService;
import com.swiftdrop.delivery.application.ParcelStatusService;
import com.swiftdrop.delivery.application.ParcelView;
import com.swiftdrop.delivery.domain.DeliveryProof;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.test.security.TestPrincipal;

/** Courier endpoints over HTTP: roles, request validation, multipart handling and error mapping. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class CourierApiTest {

    private static final UUID COURIER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CourierParcelService courierParcels;
    @MockitoBean
    private ParcelStatusService statuses;
    @MockitoBean
    private DeliveryProofService proofService;
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
        ParcelAddress address = new ParcelAddress("Kumari Silva", "+94711234567", "4 Lake View", null, "Kandy",
                UUID.randomUUID(), null);
        parcel = Parcel.forCustomer("SD7K3M9Q2XWB", UUID.randomUUID(), address, address, ParcelSize.SMALL, 1000, null,
                new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), NOW);
        ReflectionTestUtils.setField(parcel, "id", PARCEL_ID);
        parcel.assignCourier(COURIER_ID, NOW);
    }

    @Test
    void courierSeesTheDayWithContactsAndNextStatuses() throws Exception {
        when(courierParcels.today(COURIER_ID)).thenReturn(List.of(
                new CourierParcel(parcel, false, Set.of(ParcelStatus.PICKED_UP))));

        mvc.perform(get("/api/v1/courier/parcels").with(TestPrincipal.courier(COURIER_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trackingNumber").value("SD7K3M9Q2XWB"))
                .andExpect(jsonPath("$[0].dropoff.phone").value("+94711234567"))
                .andExpect(jsonPath("$[0].nextStatuses", contains("PICKED_UP")))
                .andExpect(jsonPath("$[0].proofCaptured").value(false));
    }

    @Test
    void courierEndpointsAreForCouriersOnly() throws Exception {
        for (TestPrincipal other : List.of(TestPrincipal.customer(), TestPrincipal.merchant(),
                TestPrincipal.dispatcherAdmin())) {
            mvc.perform(get("/api/v1/courier/parcels").with(other.postProcessor())).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/parcels/" + PARCEL_ID + "/status-changes").with(other.postProcessor())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"toStatus\":\"PICKED_UP\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(proofUpload("Kumari").with(other.postProcessor())).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/courier/parcels")).andExpect(status().isUnauthorized());
        verify(statuses, never()).changeStatus(any(), any(), any());
    }

    @Test
    void statusChangeReturnsTheUpdatedDetail() throws Exception {
        when(reads.detail(eq(PARCEL_ID), any())).thenReturn(new ParcelView(parcel, List.of(), true, null));

        mvc.perform(post("/api/v1/parcels/" + PARCEL_ID + "/status-changes")
                        .with(TestPrincipal.courier(COURIER_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"FAILED\",\"failureReason\":\"RECIPIENT_UNAVAILABLE\",\"note\":\"Gate locked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PARCEL_ID.toString()));
        verify(statuses).changeStatus(eq(PARCEL_ID), any(), any());
    }

    @Test
    void statusChangeErrorsMapTo404And409() throws Exception {
        when(statuses.changeStatus(any(), any(), any())).thenThrow(new ParcelNotFoundException());
        mvc.perform(statusChange("{\"toStatus\":\"PICKED_UP\"}")).andExpect(status().isNotFound());

        // doThrow: re-stubbing with when(...) would call the method and hit the first stub
        doThrow(new ParcelConflictException("Illegal status transition")).when(statuses).changeStatus(any(), any(), any());
        mvc.perform(statusChange("{\"toStatus\":\"DELIVERED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Illegal status transition"));
    }

    @Test
    void invalidStatusChangesAreBadRequests() throws Exception {
        mvc.perform(statusChange("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("toStatus")));
        mvc.perform(statusChange("{\"toStatus\":\"PICKED_UP\",\"note\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("note")));
        mvc.perform(statusChange("{\"toStatus\":\"LOST\"}")).andExpect(status().isBadRequest());
        mvc.perform(statusChange("{\"toStatus\":\"FAILED\",\"failureReason\":\"BORED\"}"))
                .andExpect(status().isBadRequest());
        verify(statuses, never()).changeStatus(any(), any(), any());
    }

    @Test
    void proofUploadPassesNameAndPhotoBytes() throws Exception {
        when(proofService.capture(eq(PARCEL_ID), any(), eq("Kumari Silva"), eq(JPEG)))
                .thenReturn(new DeliveryProof(PARCEL_ID, "Kumari Silva", JPEG, "image/jpeg", COURIER_ID, NOW));

        mvc.perform(proofUpload("Kumari Silva").file(new MockMultipartFile("photo", "door.jpg", "image/jpeg", JPEG))
                        .with(TestPrincipal.courier(COURIER_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientName").value("Kumari Silva"))
                .andExpect(jsonPath("$.hasPhoto").value(true))
                .andExpect(jsonPath("$.photoSizeBytes").value(JPEG.length));
    }

    @Test
    void proofWithoutRecipientNameIsABadRequest() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/parcels/" + PARCEL_ID + "/proof")
                        .with(TestPrincipal.courier(COURIER_ID).postProcessor()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("recipientName")));
    }

    @Test
    void rejectedPhotoIsABadRequestWithoutDetails() throws Exception {
        when(proofService.capture(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("photo must be JPEG or PNG"));

        mvc.perform(proofUpload("Kumari").file(new MockMultipartFile("photo", "x.jpg", "image/jpeg", "GIF89a".getBytes()))
                        .with(TestPrincipal.courier(COURIER_ID).postProcessor()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid request"));
    }

    @Test
    void photoIsServedWithItsTypeAndNeverCached() throws Exception {
        when(proofService.photo(eq(PARCEL_ID), any()))
                .thenReturn(Optional.of(new DeliveryProofService.Photo(JPEG, "image/jpeg")));

        mvc.perform(get("/api/v1/parcels/" + PARCEL_ID + "/proof/photo")
                        .with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(JPEG))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void missingPhotoAndHiddenParcelAre404() throws Exception {
        when(proofService.photo(eq(PARCEL_ID), any())).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/parcels/" + PARCEL_ID + "/proof/photo").with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isNotFound());

        when(proofService.photo(eq(PARCEL_ID), any())).thenThrow(new ParcelNotFoundException());
        mvc.perform(get("/api/v1/parcels/" + PARCEL_ID + "/proof/photo").with(TestPrincipal.courier().postProcessor()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/parcels/" + PARCEL_ID + "/proof/photo").with(TestPrincipal.merchant().postProcessor()))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder statusChange(String json) {
        return post("/api/v1/parcels/" + PARCEL_ID + "/status-changes")
                .with(TestPrincipal.courier(COURIER_ID).postProcessor())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private static MockMultipartHttpServletRequestBuilder proofUpload(String recipientName) {
        MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.PUT,
                "/api/v1/parcels/" + PARCEL_ID + "/proof");
        request.param("recipientName", recipientName);
        return request;
    }
}
