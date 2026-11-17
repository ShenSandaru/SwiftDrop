package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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

import com.swiftdrop.delivery.api.DispatchAction;
import com.swiftdrop.delivery.api.DispatchParcelSummary;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelAssignmentStatus;
import com.swiftdrop.delivery.api.ParcelAssignmentSummary;
import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.delivery.api.PublicTrackingView;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.Hub;
import com.swiftdrop.delivery.domain.HubRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelAssignmentRecord;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryEntry;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.pricing.api.ParcelSize;

class ParcelQueryServiceTest {

    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30Z");

    private ParcelRepository parcels;
    private ParcelAssignmentRepository assignments;
    private ParcelStatusHistoryRepository history;
    private ParcelQueryService service;
    private final HubRepository hubs = mock(HubRepository.class);
    private final DeliveryEstimator estimator = mock(DeliveryEstimator.class);
    private final StaffScopes scopes = mock(StaffScopes.class);
    private static final UUID STAFF = UUID.randomUUID();
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        parcels = mock(ParcelRepository.class);
        assignments = mock(ParcelAssignmentRepository.class);
        history = mock(ParcelStatusHistoryRepository.class);
        when(scopes.scopeOf(STAFF)).thenReturn(com.swiftdrop.delivery.domain.HubScope.EVERYWHERE);
        service = new ParcelQueryService(parcels, assignments, history, hubs,
                DeliveryPolicy.defaults(), estimator, scopes);
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
        // The final statuses of ADR 0009 §2
        assertThat(terminal.getValue()).containsExactlyInAnyOrder(ParcelStatus.DELIVERED, ParcelStatus.RETURNED,
                ParcelStatus.CANCELLED, ParcelStatus.FAILED);
    }

    @Test
    void dispatchQueryWithoutSearchReturnsValidEmptyPage() {
        when(parcels.findForDispatch(any(), any(), any(), anyBoolean(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        PageResult<DispatchParcelSummary> result = service.findDispatchParcels(STAFF, null, null, null, null, new PageRequest(0, 10));

        assertThat(result.items()).isEmpty();
        assertThat(result.totalItems()).isZero();
        assertThat(result.totalPages()).isZero();
        verify(parcels, never()).findForDispatchBySearch(any(), any(), any(), anyString(), any(), anyBoolean(), any(), any(), any());
    }

    @Test
    void activeAssignmentsFilterByTypeAndPaginateByAssignedTime() {
        for (ParcelAssignmentType type : ParcelAssignmentType.values()) {
            UUID assignmentId = UUID.randomUUID();
            UUID courierId = UUID.randomUUID();
            ParcelAssignmentSummary summary = new ParcelAssignmentSummary(
                    assignmentId, PARCEL_ID, courierId, type, ParcelAssignmentStatus.ACTIVE, NOW, null, null);
            ParcelAssignmentRecord record = mock(ParcelAssignmentRecord.class);
            when(record.toSummary()).thenReturn(summary);
            when(assignments.findInScope(eq(type), eq(ParcelAssignmentStatus.ACTIVE), anyBoolean(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(record),
                            org.springframework.data.domain.PageRequest.of(1, 1), 2));

            PageResult<ParcelAssignmentSummary> result = service.findActiveAssignments(STAFF, type, new PageRequest(1, 1));

            assertThat(result.items()).containsExactly(summary);
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.totalItems()).isEqualTo(2);
            assertThat(result.totalPages()).isEqualTo(2);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(assignments).findInScope(eq(type), eq(ParcelAssignmentStatus.ACTIVE), anyBoolean(), any(), any(), pageable.capture());
            assertThat(pageable.getValue().getSort().getOrderFor("assignedAt"))
                    .isEqualTo(Sort.Order.asc("assignedAt"));
        }
    }

    @Test
    void emptyDispatchSearchUsesQueryWithoutSearch() {
        when(parcels.findForDispatch(any(), any(), any(), anyBoolean(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        PageResult<DispatchParcelSummary> result = service.findDispatchParcels(STAFF, null, null, null, "", new PageRequest(0, 10));

        assertThat(result.items()).isEmpty();
        verify(parcels).findForDispatch(any(), any(), any(), anyBoolean(), any(), any(), any());
        verify(parcels, never()).findForDispatchBySearch(any(), any(), any(), anyString(), any(), anyBoolean(), any(), any(), any());
    }

    @Test
    void nonBlankDispatchSearchUsesTypedSearchQuery() {
        when(parcels.findForDispatchBySearch(any(), any(), any(), eq("SD7K3M9Q2XWB"), any(), anyBoolean(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        PageResult<DispatchParcelSummary> result = service.findDispatchParcels(STAFF, null, null, null, " SD7K3M9Q2XWB ", new PageRequest(0, 10));

        assertThat(result.items()).isEmpty();
        verify(parcels).findForDispatchBySearch(any(), any(), any(), eq("SD7K3M9Q2XWB"), any(), anyBoolean(), any(), any(), any());
        verify(parcels, never()).findForDispatch(any(), any(), any(), anyBoolean(), any(), any(), any());
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
    void publicViewCarriesTheEstimateCountedFromThePickup() {
        Instant pickedUp = NOW.plusSeconds(3600);
        when(parcels.findByTrackingNumber("SD7K3M9Q2XWB")).thenReturn(Optional.of(parcel));
        when(history.findByParcelIdOrderBySequenceAsc(PARCEL_ID)).thenReturn(List.of(
                new ParcelStatusHistoryEntry(PARCEL_ID, 1, null, ParcelStatus.CREATED, null, ActorRole.MERCHANT, null,
                        null, UUID.randomUUID(), NOW),
                new ParcelStatusHistoryEntry(PARCEL_ID, 2, ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null,
                        ActorRole.COURIER, null, null, UUID.randomUUID(), pickedUp)));
        when(estimator.estimate(parcel, pickedUp)).thenReturn(Optional.of(java.time.LocalDate.parse("2026-11-06")));

        assertThat(service.findPublicTrackingView("SD7K3M9Q2XWB").orElseThrow().estimatedDelivery())
                .isEqualTo("2026-11-06");
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

    @Test
    void staleParcelsAreUnfinishedOnesLongestWaitingFirst() {
        Instant cutoff = NOW.minusSeconds(86_400);
        ArgumentCaptor<Collection<ParcelStatus>> finished = ArgumentCaptor.captor();
        ArgumentCaptor<org.springframework.data.domain.Pageable> pageable = ArgumentCaptor.captor();
        when(parcels.findStale(any(), eq(cutoff), anyBoolean(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(parcel)));

        PageResult<DispatchParcelSummary> result = service.findStaleParcels(STAFF, cutoff, new PageRequest(0, 10));

        assertThat(result.items()).singleElement()
                .satisfies(summary -> assertThat(summary.trackingNumber()).isEqualTo("SD7K3M9Q2XWB"));
        verify(parcels).findStale(finished.capture(), eq(cutoff), anyBoolean(), any(), any(), pageable.capture());
        assertThat(finished.getValue()).containsExactlyInAnyOrder(ParcelStatus.DELIVERED, ParcelStatus.RETURNED,
                ParcelStatus.CANCELLED, ParcelStatus.FAILED);
        assertThat(pageable.getValue().getSort().getOrderFor("updatedAt").isAscending()).isTrue();
    }

    // ---- Hub scope (ADR 0012) ----

    private static final UUID BADULLA = UUID.randomUUID();
    private static final UUID KANDY = UUID.randomUUID();
    private static final UUID BADULLA_ZONE = UUID.randomUUID();

    @Test
    void aStaffMemberWithHubsAsksOnlyForTheirHubsAndTheirPickupZones() {
        UUID dispatcher = UUID.randomUUID();
        when(scopes.scopeOf(dispatcher)).thenReturn(com.swiftdrop.delivery.domain.HubScope.of(java.util.Set.of(BADULLA)));
        when(hubs.findAllById(java.util.Set.of(BADULLA))).thenReturn(List.of(
                new Hub("BAD", "Badulla branch", "Badulla", BADULLA_ZONE, false, NOW)));
        when(parcels.findForDispatch(any(), any(), any(), anyBoolean(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.findDispatchParcels(dispatcher, null, null, null, null, new PageRequest(0, 10));

        verify(parcels).findForDispatch(any(), any(), any(), eq(false), eq(java.util.Set.of(BADULLA)),
                eq(java.util.Set.of(BADULLA_ZONE)), any());
    }

    @Test
    void aStaffMemberWithoutHubsGetsAQueryThatMatchesNothing() {
        UUID dispatcher = UUID.randomUUID();
        when(scopes.scopeOf(dispatcher)).thenReturn(com.swiftdrop.delivery.domain.HubScope.NONE);
        when(parcels.findStale(any(), any(), anyBoolean(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.findStaleParcels(dispatcher, NOW, new PageRequest(0, 10));

        verify(parcels).findStale(any(), eq(NOW), eq(false), eq(ParcelRepository.NOBODY), eq(ParcelRepository.NOBODY),
                any());
    }

    @Test
    void staffWhoWorkEverywhereSkipTheHubFilter() {
        when(parcels.findForDispatch(any(), any(), any(), anyBoolean(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.findDispatchParcels(STAFF, null, null, null, null, new PageRequest(0, 10));

        verify(parcels).findForDispatch(any(), any(), any(), eq(true), eq(ParcelRepository.NOBODY),
                eq(ParcelRepository.NOBODY), any());
    }

    @Test
    void aParcelTheCallerOnlyFollowsIsReadOnlyWithoutActions() {
        UUID dispatcher = UUID.randomUUID();
        // The parcel travels Badulla to Kandy; it is on the truck to Kandy, so Badulla only follows
        Hub badulla = new Hub("BAD", "Badulla branch", "Badulla", BADULLA_ZONE, false, NOW);
        Hub kandy = new Hub("KDY", "Kandy branch", "Kandy", UUID.randomUUID(), false, NOW);
        ReflectionTestUtils.setField(parcel, "status", ParcelStatus.IN_TRANSIT);
        ReflectionTestUtils.setField(parcel, "originHubId", badulla.getId());
        ReflectionTestUtils.setField(parcel, "destinationHubId", kandy.getId());
        ReflectionTestUtils.setField(parcel, "nextHubId", kandy.getId());
        when(scopes.scopeOf(dispatcher)).thenReturn(
                com.swiftdrop.delivery.domain.HubScope.of(java.util.Set.of(badulla.getId())));
        when(hubs.findAllById(any())).thenReturn(List.of(badulla));
        when(hubs.findAll()).thenReturn(List.of(badulla, kandy));
        when(parcels.findForDispatch(any(), any(), any(), anyBoolean(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(parcel)));

        DispatchParcelSummary summary = service.findDispatchParcels(dispatcher, null, null, null, null,
                new PageRequest(0, 10)).items().get(0);

        assertThat(summary.readOnly()).isTrue();
        assertThat(summary.actions()).isEmpty();
    }

    @Test
    void aParcelInTheCallersHandsKeepsItsActions() {
        Hub badulla = new Hub("BAD", "Badulla branch", "Badulla", BADULLA_ZONE, false, NOW);
        Hub kandy = new Hub("KDY", "Kandy branch", "Kandy", UUID.randomUUID(), false, NOW);
        ReflectionTestUtils.setField(parcel, "status", ParcelStatus.IN_TRANSIT);
        ReflectionTestUtils.setField(parcel, "originHubId", badulla.getId());
        ReflectionTestUtils.setField(parcel, "destinationHubId", kandy.getId());
        ReflectionTestUtils.setField(parcel, "nextHubId", kandy.getId());
        when(hubs.findAll()).thenReturn(List.of(badulla, kandy));
        when(parcels.findForDispatch(any(), any(), any(), anyBoolean(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(parcel)));

        DispatchParcelSummary summary = service.findDispatchParcels(STAFF, null, null, null, null,
                new PageRequest(0, 10)).items().get(0);

        assertThat(summary.readOnly()).isFalse();
        assertThat(summary.actions()).contains(DispatchAction.SCAN_AT_HUB);
    }
}
