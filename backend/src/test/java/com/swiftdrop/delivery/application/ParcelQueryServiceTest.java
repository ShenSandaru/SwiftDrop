package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.delivery.api.PublicTrackingView;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryEntry;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.pricing.api.ParcelSize;

class ParcelQueryServiceTest {

    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30Z");

    private ParcelRepository parcels;
    private ParcelStatusHistoryRepository history;
    private ParcelQueryService service;
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        parcels = mock(ParcelRepository.class);
        history = mock(ParcelStatusHistoryRepository.class);
        service = new ParcelQueryService(parcels, history);
        parcel = Parcel.forMerchant("SD7K3M9Q2XWB", MERCHANT_ID, "ORD-1",
                new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo", UUID.randomUUID(),
                        "00500"),
                new ParcelAddress("Kumari Silva", "+94711234567", "4 Lake View", null, "Kandy", UUID.randomUUID(),
                        "20000"),
                ParcelSize.SMALL, 1000, "Books", new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()),
                NOW);
        ReflectionTestUtils.setField(parcel, "id", PARCEL_ID);
    }

    @Test
    void unassignedParcelsAreCreatedWithoutCourierOldestFirst() {
        when(parcels.findByStatusAndAssignedCourierIdIsNull(eq(ParcelStatus.CREATED), any()))
                .thenReturn(new PageImpl<>(List.of(parcel), org.springframework.data.domain.PageRequest.of(1, 5), 6));

        PageResult<ParcelSummary> result = service.findUnassignedParcels(new PageRequest(1, 5));

        assertThat(result.items()).extracting(ParcelSummary::id).containsExactly(PARCEL_ID);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalItems()).isEqualTo(6);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(sortOf(captureUnassignedPageable()).getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void workloadCountsMapCourierToActiveParcelsAndExcludeFinalStatuses() {
        UUID courierA = UUID.randomUUID();
        UUID courierB = UUID.randomUUID();
        when(parcels.countActiveByCourier(any())).thenReturn(List.of(
                new Object[] {courierA, 3L}, new Object[] {courierB, 1L}));

        assertThat(service.countActiveParcelsByCourier()).containsOnly(
                entry(courierA, 3L),
                entry(courierB, 1L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ParcelStatus>> terminal = ArgumentCaptor.forClass(Collection.class);
        verify(parcels).countActiveByCourier(terminal.capture());
        assertThat(terminal.getValue()).containsExactlyInAnyOrder(ParcelStatus.DELIVERED, ParcelStatus.FAILED);
    }

    @Test
    void merchantListIsScopedToTheMerchantNewestFirstWithOptionalStatus() {
        when(parcels.findByMerchantId(eq(MERCHANT_ID), any())).thenReturn(new PageImpl<>(List.of(parcel)));
        when(parcels.findByMerchantIdAndStatus(eq(MERCHANT_ID), eq(ParcelStatus.CREATED), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.findParcelsByMerchant(MERCHANT_ID, null, new PageRequest(0, 20)).items()).hasSize(1);
        assertThat(service.findParcelsByMerchant(MERCHANT_ID, ParcelStatus.CREATED, new PageRequest(0, 20)).items())
                .isEmpty();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(parcels).findByMerchantId(eq(MERCHANT_ID), pageable.capture());
        assertThat(sortOf(pageable.getValue()).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void merchantReferenceLookupTrimsAndRejectsBlankInput() {
        when(parcels.findByMerchantIdAndMerchantOrderReference(MERCHANT_ID, "ORD-1")).thenReturn(Optional.of(parcel));

        assertThat(service.findByMerchantReference(MERCHANT_ID, " ORD-1 ")).get()
                .extracting(ParcelSummary::merchantOrderReference).isEqualTo("ORD-1");
        assertThat(service.findByMerchantReference(MERCHANT_ID, " ")).isEmpty();
        assertThat(service.findByMerchantReference(null, "ORD-1")).isEmpty();
    }

    @Test
    void publicViewIsCaseInsensitiveAndHasOnlyStatusCitiesAndTimes() {
        when(parcels.findByTrackingNumber("SD7K3M9Q2XWB")).thenReturn(Optional.of(parcel));
        when(history.findByParcelIdOrderBySequenceAsc(PARCEL_ID)).thenReturn(List.of(new ParcelStatusHistoryEntry(
                PARCEL_ID, 1, null, ParcelStatus.CREATED, null, ActorRole.MERCHANT, null, null, UUID.randomUUID(), NOW)));

        PublicTrackingView view = service.findPublicTrackingView(" sd7k3m9q2xwb ").orElseThrow();

        assertThat(view.trackingNumber()).isEqualTo("SD7K3M9Q2XWB");
        assertThat(view.pickupCity()).isEqualTo("Colombo");
        assertThat(view.dropoffCity()).isEqualTo("Kandy");
        assertThat(view.history()).singleElement()
                .satisfies(event -> assertThat(event.occurredAt()).isEqualTo(NOW));
        assertThat(view.toString()).doesNotContain("Nimal", "Kumari", "+947", "Temple", "Lake", "00500", "Books",
                "ORD-1", MERCHANT_ID.toString());
    }

    @Test
    void malformedTrackingNumbersNeverReachTheDatabase() {
        for (String input : new String[] {null, "", "SD123", "' OR 1=1 --", "SD7K3M9Q2XWI"}) {
            assertThat(service.findPublicTrackingView(input)).isEmpty();
        }
        verify(parcels, never()).findByTrackingNumber(anyString());
    }

    private Pageable captureUnassignedPageable() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(parcels).findByStatusAndAssignedCourierIdIsNull(eq(ParcelStatus.CREATED), pageable.capture());
        return pageable.getValue();
    }

    private static Sort.Order sortOf(Pageable pageable) {
        return pageable.getSort().getOrderFor("createdAt");
    }
}
