package com.swiftdrop.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.DispatchAction;
import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.pricing.api.ParcelSize;

/** A parcel through the hub network (ADR 0009 §3): Kandy branch → Colombo hub → Jaffna branch. */
class ParcelHubFlowTest {

    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");
    private static final int MAX_ATTEMPTS = 3;
    private static final UUID KANDY = UUID.randomUUID();
    private static final UUID COLOMBO = UUID.randomUUID();
    private static final UUID JAFFNA = UUID.randomUUID();
    private static final HubRoute ROUTE = new HubRoute(KANDY, COLOMBO, JAFFNA);

    private final UUID pickupCourier = UUID.randomUUID();
    private final UUID deliveryCourier = UUID.randomUUID();
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Kandy",
                UUID.randomUUID(), null);
        parcel = Parcel.forCustomer("SD7K3M9Q2XWB", UUID.randomUUID(), address, address, ParcelSize.SMALL, 1000, null,
                new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), NOW);
        ReflectionTestUtils.setField(parcel, "id", UUID.randomUUID());
        parcel.assignPickupCourier(pickupCourier, NOW);
    }

    /** Pickup, origin hub, central hub, destination hub, ready for a delivery courier. */
    private void toDestinationHub() {
        parcel.pickUp(pickupCourier, ROUTE, NOW);
        parcel.scanAtHub(KANDY, NOW);
        parcel.sendToNextHub(NOW);
        parcel.scanAtHub(COLOMBO, NOW);
        parcel.sendToNextHub(NOW);
        parcel.scanAtHub(JAFFNA, NOW);
    }

    @Test
    void parcelTravelsItsRouteAndIsDelivered() {
        parcel.pickUp(pickupCourier, ROUTE, NOW);
        assertThat(parcel.getAssignedCourierId()).as("pickup task ends at pickup").isNull();
        assertThat(parcel.getRoute()).isEqualTo(ROUTE);

        parcel.scanAtHub(KANDY, NOW);
        assertThat(parcel.getCurrentHubId()).isEqualTo(KANDY);
        parcel.sendToNextHub(NOW);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(parcel.getNextHubId()).isEqualTo(COLOMBO);
        assertThat(parcel.getCurrentHubId()).isNull();
        parcel.scanAtHub(COLOMBO, NOW);
        parcel.sendToNextHub(NOW);
        parcel.scanAtHub(JAFFNA, NOW);
        assertThat(parcel.isAtDestinationHub()).isTrue();

        parcel.assignDeliveryCourier(deliveryCourier, MAX_ATTEMPTS, NOW);
        parcel.startDelivery(deliveryCourier, MAX_ATTEMPTS, NOW);
        assertThat(parcel.getDeliveryAttempts()).isEqualTo(1);
        parcel.deliver(deliveryCourier, true, NOW);

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(parcel.getAssignedCourierId()).isNull();
        // CREATED, PICKED_UP, 3 scans, 2 legs, out for delivery, delivered
        assertThat(parcel.getLastSequence()).isEqualTo(9);
    }

    @Test
    void parcelCannotSkipOrLeaveItsRoute() {
        parcel.pickUp(pickupCourier, ROUTE, NOW);

        assertThatThrownBy(() -> parcel.scanAtHub(COLOMBO, NOW))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The parcel is not routed to this hub");
        parcel.scanAtHub(KANDY, NOW);
        assertThatThrownBy(() -> parcel.scanAtHub(KANDY, NOW)).isInstanceOf(ParcelConflictException.class);
        parcel.sendToNextHub(NOW);
        assertThatThrownBy(() -> parcel.scanAtHub(JAFFNA, NOW))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The parcel is not routed to this hub");
    }

    @Test
    void deliveryStartsOnlyAtTheDestinationHub() {
        parcel.pickUp(pickupCourier, ROUTE, NOW);
        parcel.scanAtHub(KANDY, NOW);

        assertThatThrownBy(() -> parcel.assignDeliveryCourier(deliveryCourier, MAX_ATTEMPTS, NOW))
                .isInstanceOf(ParcelConflictException.class);
        parcel.sendToNextHub(NOW);
        parcel.scanAtHub(COLOMBO, NOW);
        parcel.sendToNextHub(NOW);
        parcel.scanAtHub(JAFFNA, NOW);
        assertThat(parcel.getNextHubId()).isNull();
        assertThatThrownBy(() -> parcel.sendToNextHub(NOW))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The parcel is already at its destination hub");
    }

    @Test
    void failedAttemptsAreRetriedUpToTheLimitThenReturned() {
        toDestinationHub();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            parcel.assignDeliveryCourier(deliveryCourier, MAX_ATTEMPTS, NOW);
            parcel.startDelivery(deliveryCourier, MAX_ATTEMPTS, NOW);
            parcel.failAttempt(deliveryCourier, FailureReason.RECIPIENT_UNAVAILABLE, NOW);
            assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.DELIVERY_ATTEMPT_FAILED);
            if (attempt < MAX_ATTEMPTS) {
                assertThatThrownBy(() -> parcel.startReturn(MAX_ATTEMPTS, NOW)).isInstanceOf(ParcelConflictException.class);
            }
            parcel.scanAtHub(JAFFNA, NOW);
        }

        assertThat(parcel.getDeliveryAttempts()).isEqualTo(3);
        assertThatThrownBy(() -> parcel.assignDeliveryCourier(deliveryCourier, MAX_ATTEMPTS, NOW))
                .isInstanceOf(ParcelConflictException.class).hasMessageContaining("start the return");
        parcel.startReturn(MAX_ATTEMPTS, NOW);
        UUID returnCourier = UUID.randomUUID();
        parcel.assignReturnCourier(returnCourier, NOW);
        parcel.completeReturn(returnCourier, true, NOW);

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.RETURNED);
        assertThat(parcel.getLastFailureReason()).isEqualTo(FailureReason.RECIPIENT_UNAVAILABLE);
    }

    @Test
    void refusalIsReturnedAtOnce() {
        toDestinationHub();
        parcel.assignDeliveryCourier(deliveryCourier, MAX_ATTEMPTS, NOW);
        parcel.startDelivery(deliveryCourier, MAX_ATTEMPTS, NOW);
        parcel.failAttempt(deliveryCourier, FailureReason.REFUSED, NOW);
        parcel.scanAtHub(JAFFNA, NOW);

        assertThat(parcel.canAttemptDelivery(MAX_ATTEMPTS)).isFalse();
        parcel.startReturn(MAX_ATTEMPTS, NOW);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.RETURNING);
    }

    @Test
    void damageIsNotAnAttemptReasonButAFailure() {
        toDestinationHub();
        parcel.assignDeliveryCourier(deliveryCourier, MAX_ATTEMPTS, NOW);
        parcel.startDelivery(deliveryCourier, MAX_ATTEMPTS, NOW);

        assertThatThrownBy(() -> parcel.failAttempt(deliveryCourier, FailureReason.DAMAGED, NOW))
                .isInstanceOf(ParcelConflictException.class);
        assertThatThrownBy(() -> parcel.failAttempt(deliveryCourier, null, NOW))
                .isInstanceOf(ParcelConflictException.class).hasMessage("A failure reason is required");
        parcel.markFailed(FailureReason.DAMAGED, NOW);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.FAILED);
        assertThat(parcel.getAssignedCourierId()).isNull();
    }

    @Test
    void onlyTheAssignedCourierActsAndOnlyWithProof() {
        toDestinationHub();
        parcel.assignDeliveryCourier(deliveryCourier, MAX_ATTEMPTS, NOW);

        assertThatThrownBy(() -> parcel.startDelivery(pickupCourier, MAX_ATTEMPTS, NOW))
                .isInstanceOf(ParcelNotFoundException.class);
        parcel.startDelivery(deliveryCourier, MAX_ATTEMPTS, NOW);
        assertThatThrownBy(() -> parcel.deliver(deliveryCourier, false, NOW))
                .isInstanceOf(ParcelConflictException.class).hasMessage("Proof of delivery is required");
    }

    @Test
    void cancellationOnlyBeforePickup() {
        parcel.cancel(NOW);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CANCELLED);
        assertThat(parcel.getAssignedCourierId()).isNull();
        assertThatThrownBy(() -> parcel.pickUp(pickupCourier, ROUTE, NOW)).isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void pickedUpParcelCannotBeCancelledAndUnpickedCannotFail() {
        assertThatThrownBy(() -> parcel.markFailed(FailureReason.OTHER, NOW))
                .isInstanceOf(ParcelConflictException.class).hasMessageContaining("cancelled, not failed");
        parcel.pickUp(pickupCourier, ROUTE, NOW);
        assertThatThrownBy(() -> parcel.cancel(NOW))
                .isInstanceOf(ParcelConflictException.class).hasMessage("Illegal status transition");
    }

    @Test
    void finalStatusesAcceptNothing() {
        parcel.cancel(NOW);
        assertThatThrownBy(() -> parcel.markFailed(FailureReason.OTHER, NOW))
                .isInstanceOf(ParcelConflictException.class).hasMessage("Parcel is already in a final status");
    }

    @Test
    void parcelPickedUpWithoutARouteFollowsTheOldRules() {
        parcel.pickUp(pickupCourier, null, NOW);

        assertThat(parcel.isLegacy()).isTrue();
        assertThat(parcel.getAssignedCourierId()).as("the same courier to the end").isEqualTo(pickupCourier);
        assertThatThrownBy(() -> parcel.scanAtHub(KANDY, NOW)).isInstanceOf(ParcelConflictException.class);
        parcel.changeStatus(ParcelStatus.IN_TRANSIT, pickupCourier, null, false, NOW);
        parcel.changeStatus(ParcelStatus.OUT_FOR_DELIVERY, pickupCourier, null, false, NOW);
        parcel.changeStatus(ParcelStatus.DELIVERED, pickupCourier, null, true, NOW);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.DELIVERED);
    }

    @Test
    void hubParcelsCannotUseTheOldCourierSteps() {
        parcel.pickUp(pickupCourier, ROUTE, NOW);
        parcel.scanAtHub(KANDY, NOW);
        ReflectionTestUtils.setField(parcel, "assignedCourierId", pickupCourier);

        assertThatThrownBy(() -> parcel.changeStatus(ParcelStatus.IN_TRANSIT, pickupCourier, null, false, NOW))
                .isInstanceOf(ParcelConflictException.class).hasMessage("Illegal status transition");
    }

    @Test
    void dispatcherActionsFollowTheJourney() {
        assertThat(parcel.dispatchActions(MAX_ATTEMPTS)).containsExactlyInAnyOrder(DispatchAction.ASSIGN_PICKUP,
                DispatchAction.CANCEL);

        parcel.pickUp(pickupCourier, ROUTE, NOW);
        assertThat(parcel.dispatchActions(MAX_ATTEMPTS))
                .containsExactlyInAnyOrder(DispatchAction.SCAN_AT_HUB, DispatchAction.MARK_FAILED);
        assertThat(parcel.expectedHubId()).isEqualTo(KANDY);

        parcel.scanAtHub(KANDY, NOW);
        assertThat(parcel.dispatchActions(MAX_ATTEMPTS))
                .containsExactlyInAnyOrder(DispatchAction.SEND_TO_NEXT_HUB, DispatchAction.MARK_FAILED);
        assertThat(parcel.expectedHubId()).isNull();

        parcel.sendToNextHub(NOW);
        assertThat(parcel.expectedHubId()).isEqualTo(COLOMBO);
        parcel.scanAtHub(COLOMBO, NOW);
        parcel.sendToNextHub(NOW);
        parcel.scanAtHub(JAFFNA, NOW);
        assertThat(parcel.dispatchActions(MAX_ATTEMPTS))
                .containsExactlyInAnyOrder(DispatchAction.ASSIGN_DELIVERY, DispatchAction.MARK_FAILED);

        parcel.assignDeliveryCourier(deliveryCourier, MAX_ATTEMPTS, NOW);
        parcel.startDelivery(deliveryCourier, MAX_ATTEMPTS, NOW);
        assertThat(parcel.dispatchActions(MAX_ATTEMPTS)).containsExactly(DispatchAction.MARK_FAILED);
        parcel.failAttempt(deliveryCourier, FailureReason.ADDRESS_NOT_FOUND, NOW);
        assertThat(parcel.expectedHubId()).isEqualTo(JAFFNA);
        parcel.scanAtHub(JAFFNA, NOW);
        assertThat(parcel.dispatchActions(MAX_ATTEMPTS))
                .as("an unknown address is returned at once")
                .containsExactlyInAnyOrder(DispatchAction.START_RETURN, DispatchAction.MARK_FAILED);

        parcel.startReturn(MAX_ATTEMPTS, NOW);
        assertThat(parcel.dispatchActions(MAX_ATTEMPTS))
                .containsExactlyInAnyOrder(DispatchAction.ASSIGN_RETURN, DispatchAction.MARK_FAILED);
        parcel.markFailed(FailureReason.OTHER, NOW);
        assertThat(parcel.dispatchActions(MAX_ATTEMPTS)).isEmpty();
    }

    @Test
    void legacyParcelsOfferOnlyMarkingFailed() {
        parcel.pickUp(pickupCourier, null, NOW);

        assertThat(parcel.dispatchActions(MAX_ATTEMPTS)).containsExactly(DispatchAction.MARK_FAILED);
        assertThat(parcel.expectedHubId()).isNull();
    }
}
