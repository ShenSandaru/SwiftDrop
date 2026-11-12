package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.CourierParcelService.CourierParcel;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelAssignmentRecord;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.pricing.api.ParcelSize;

/** The courier's run built from their tasks (D-09, ADR 0009 §5). */
class CourierParcelServiceTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final UUID COURIER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID DISPATCHER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID ORIGIN_HUB = UUID.randomUUID();
    private static final UUID DESTINATION_HUB = UUID.randomUUID();
    private static final Instant MORNING = Instant.parse("2026-11-03T03:00:00Z");

    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final ParcelAssignmentRepository assignments = mock(ParcelAssignmentRepository.class);
    private final DeliveryProofRepository proofs = mock(DeliveryProofRepository.class);
    /** The parcel behind each task, so {@link #day} can answer the parcel lookup. */
    private final Map<ParcelAssignmentRecord, Parcel> taskParcels = new IdentityHashMap<>();

    @Test
    void todayStartsAtMidnightInColomboNotUtc() {
        // 01:00 on 4 Nov in Colombo is still 3 Nov in UTC
        CourierParcelService service = at("2026-11-03T19:30:00Z");

        assertThat(service.startOfToday()).isEqualTo(Instant.parse("2026-11-03T18:30:00Z"));
    }

    @Test
    void justBeforeColomboMidnightBelongsToThePreviousDay() {
        CourierParcelService service = at("2026-11-03T18:29:59Z");

        assertThat(service.startOfToday()).isEqualTo(Instant.parse("2026-11-02T18:30:00Z"));
    }

    @Test
    void eachTaskOffersOnlyItsOwnSteps() {
        Parcel toCollect = parcel(ParcelStatus.CREATED, false);
        Parcel atDestination = parcel(ParcelStatus.AT_HUB, true);
        ReflectionTestUtils.setField(atDestination, "currentHubId", DESTINATION_HUB);
        Parcel outForDelivery = parcel(ParcelStatus.OUT_FOR_DELIVERY, true);
        Parcel returning = parcel(ParcelStatus.RETURNING, true);
        day(task(toCollect, ParcelAssignmentType.PICKUP, false), task(atDestination, ParcelAssignmentType.DELIVERY, false),
                task(outForDelivery, ParcelAssignmentType.DELIVERY, false), task(returning, ParcelAssignmentType.RETURN, false));

        List<CourierParcel> today = at("2026-11-03T10:00:00Z").today(COURIER_ID);

        assertThat(today).extracting(CourierParcel::task).containsExactly(ParcelAssignmentType.PICKUP,
                ParcelAssignmentType.DELIVERY, ParcelAssignmentType.DELIVERY, ParcelAssignmentType.RETURN);
        assertThat(today.get(0).nextStatuses()).containsExactly(ParcelStatus.PICKED_UP);
        assertThat(today.get(1).nextStatuses()).containsExactly(ParcelStatus.OUT_FOR_DELIVERY);
        assertThat(today.get(2).nextStatuses())
                .containsExactlyInAnyOrder(ParcelStatus.DELIVERED, ParcelStatus.DELIVERY_ATTEMPT_FAILED);
        assertThat(today.get(3).nextStatuses()).containsExactly(ParcelStatus.RETURNED);
    }

    @Test
    void finishedTasksStayOnTheListWithNothingToDo() {
        Parcel atOriginHub = parcel(ParcelStatus.AT_HUB, true);
        ReflectionTestUtils.setField(atOriginHub, "currentHubId", ORIGIN_HUB);
        Parcel delivered = parcel(ParcelStatus.DELIVERED, true);
        day(task(atOriginHub, ParcelAssignmentType.PICKUP, true), task(delivered, ParcelAssignmentType.DELIVERY, true));
        when(proofs.findParcelIdsWithProof(any())).thenReturn(Set.of(delivered.getId()));

        List<CourierParcel> today = at("2026-11-03T10:00:00Z").today(COURIER_ID);

        // The pickup courier is done once the parcel reaches the hub, though the parcel is not finished
        assertThat(today).extracting(CourierParcel::taskDone).containsExactly(true, true);
        assertThat(today).allSatisfy(item -> assertThat(item.nextStatuses()).isEmpty());
        assertThat(today).extracting(CourierParcel::proofCaptured).containsExactly(false, true);
    }

    @Test
    void theLatestTaskPerParcelIsShown() {
        Parcel parcel = parcel(ParcelStatus.OUT_FOR_DELIVERY, true);
        ParcelAssignmentRecord failedAttempt = task(parcel, ParcelAssignmentType.DELIVERY, true);
        ParcelAssignmentRecord secondAttempt = task(parcel, ParcelAssignmentType.DELIVERY, false);
        day(failedAttempt, secondAttempt);

        List<CourierParcel> today = at("2026-11-03T10:00:00Z").today(COURIER_ID);

        assertThat(today).singleElement().satisfies(item -> assertThat(item.taskDone()).isFalse());
    }

    @Test
    void legacyParcelsKeepTheAdr0006Steps() {
        Parcel pickedUpWithoutRoute = parcel(ParcelStatus.PICKED_UP, false);
        day(task(pickedUpWithoutRoute, ParcelAssignmentType.PICKUP, false));

        assertThat(at("2026-11-03T10:00:00Z").today(COURIER_ID).getFirst().nextStatuses())
                .containsExactlyInAnyOrder(ParcelStatus.IN_TRANSIT, ParcelStatus.FAILED);
    }

    @Test
    void listAsksForTasksSinceColomboMidnight() {
        when(assignments.findCourierDay(any(), any())).thenReturn(List.of());

        assertThat(at("2026-11-03T10:00:00Z").today(COURIER_ID)).isEmpty();
        verify(assignments).findCourierDay(eq(COURIER_ID), eq(Instant.parse("2026-11-02T18:30:00Z")));
        verifyNoInteractions(proofs);
    }

    private void day(ParcelAssignmentRecord... tasks) {
        when(assignments.findCourierDay(eq(COURIER_ID), any())).thenReturn(List.of(tasks));
        List<Parcel> involved = List.of(tasks).stream().map(taskParcels::get).distinct().toList();
        when(parcels.findAllById(any())).thenReturn(involved);
    }

    private ParcelAssignmentRecord task(Parcel parcel, ParcelAssignmentType type, boolean done) {
        ParcelAssignmentRecord task = new ParcelAssignmentRecord(parcel.getId(), COURIER_ID, type, DISPATCHER_ID, MORNING);
        if (done) {
            task.complete(MORNING.plusSeconds(3600));
        }
        taskParcels.put(task, parcel);
        return task;
    }

    private CourierParcelService at(String instant) {
        return new CourierParcelService(parcels, assignments, proofs, COLOMBO,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private static Parcel parcel(ParcelStatus status, boolean routed) {
        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null);
        Parcel parcel = Parcel.forCustomer("SD7K3M9Q2XWB", UUID.randomUUID(), address, address, ParcelSize.SMALL, 1000,
                null, new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), MORNING);
        ReflectionTestUtils.setField(parcel, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(parcel, "status", status);
        if (routed) {
            ReflectionTestUtils.setField(parcel, "originHubId", ORIGIN_HUB);
            ReflectionTestUtils.setField(parcel, "destinationHubId", DESTINATION_HUB);
        }
        return parcel;
    }
}
