package com.swiftdrop.delivery.domain;

import static com.swiftdrop.delivery.api.ParcelStatus.AT_HUB;
import static com.swiftdrop.delivery.api.ParcelStatus.CANCELLED;
import static com.swiftdrop.delivery.api.ParcelStatus.CREATED;
import static com.swiftdrop.delivery.api.ParcelStatus.DELIVERED;
import static com.swiftdrop.delivery.api.ParcelStatus.DELIVERY_ATTEMPT_FAILED;
import static com.swiftdrop.delivery.api.ParcelStatus.FAILED;
import static com.swiftdrop.delivery.api.ParcelStatus.IN_TRANSIT;
import static com.swiftdrop.delivery.api.ParcelStatus.OUT_FOR_DELIVERY;
import static com.swiftdrop.delivery.api.ParcelStatus.PICKED_UP;
import static com.swiftdrop.delivery.api.ParcelStatus.RETURNED;
import static com.swiftdrop.delivery.api.ParcelStatus.RETURNING;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * Every status pair against both transition tables: the hub network of ADR 0009 §3, and the
 * ADR 0006 §1 rules that parcels picked up before the hub network keep.
 */
class ParcelLifecycleTest {

    private static final Set<String> HUB = Set.of(
            "CREATED->PICKED_UP",
            "CREATED->CANCELLED",
            "PICKED_UP->AT_HUB",
            "PICKED_UP->FAILED",
            "AT_HUB->IN_TRANSIT",
            "AT_HUB->OUT_FOR_DELIVERY",
            "AT_HUB->RETURNING",
            "AT_HUB->FAILED",
            "IN_TRANSIT->AT_HUB",
            "IN_TRANSIT->FAILED",
            "OUT_FOR_DELIVERY->DELIVERED",
            "OUT_FOR_DELIVERY->DELIVERY_ATTEMPT_FAILED",
            "OUT_FOR_DELIVERY->FAILED",
            "DELIVERY_ATTEMPT_FAILED->AT_HUB",
            "DELIVERY_ATTEMPT_FAILED->FAILED",
            "RETURNING->RETURNED",
            "RETURNING->FAILED");

    private static final Set<String> LEGACY = Set.of(
            "PICKED_UP->IN_TRANSIT",
            "PICKED_UP->FAILED",
            "IN_TRANSIT->OUT_FOR_DELIVERY",
            "IN_TRANSIT->FAILED",
            "OUT_FOR_DELIVERY->DELIVERED",
            "OUT_FOR_DELIVERY->FAILED");

    static Stream<Arguments> allPairs() {
        return Stream.of(ParcelStatus.values())
                .flatMap(from -> Stream.of(ParcelStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "hub network: {0} -> {1}")
    @MethodSource("allPairs")
    void onlyTheAdr0009TransitionsExistInTheHubNetwork(ParcelStatus from, ParcelStatus to) {
        assertThat(ParcelLifecycle.canMove(from, to, false)).isEqualTo(HUB.contains(from + "->" + to));
    }

    @ParameterizedTest(name = "legacy: {0} -> {1}")
    @MethodSource("allPairs")
    void onlyTheAdr0006TransitionsExistForLegacyParcels(ParcelStatus from, ParcelStatus to) {
        assertThat(ParcelLifecycle.canMove(from, to, true)).isEqualTo(LEGACY.contains(from + "->" + to));
    }

    @Test
    void deliveredReturnedCancelledAndFailedAreFinal() {
        assertThat(Stream.of(ParcelStatus.values()).filter(ParcelLifecycle::isTerminal))
                .containsExactlyInAnyOrder(DELIVERED, RETURNED, CANCELLED, FAILED);
    }

    @Test
    void nextStatusesMatchTheHubTable() {
        assertThat(ParcelLifecycle.nextStatuses(CREATED, false)).containsExactlyInAnyOrder(PICKED_UP, CANCELLED);
        assertThat(ParcelLifecycle.nextStatuses(AT_HUB, false))
                .containsExactlyInAnyOrder(IN_TRANSIT, OUT_FOR_DELIVERY, RETURNING, FAILED);
        assertThat(ParcelLifecycle.nextStatuses(OUT_FOR_DELIVERY, false))
                .containsExactlyInAnyOrder(DELIVERED, DELIVERY_ATTEMPT_FAILED, FAILED);
        assertThat(ParcelLifecycle.nextStatuses(DELIVERY_ATTEMPT_FAILED, false)).containsExactlyInAnyOrder(AT_HUB, FAILED);
        assertThat(ParcelLifecycle.nextStatuses(RETURNED, false)).isEmpty();
        assertThat(ParcelLifecycle.nextStatuses(CANCELLED, false)).isEmpty();
    }

    @Test
    void legacyParcelsKeepTheOldSteps() {
        assertThat(ParcelLifecycle.nextStatuses(PICKED_UP, true)).containsExactlyInAnyOrder(IN_TRANSIT, FAILED);
        assertThat(ParcelLifecycle.nextStatuses(IN_TRANSIT, true)).containsExactlyInAnyOrder(OUT_FOR_DELIVERY, FAILED);
        assertThat(ParcelLifecycle.nextStatuses(OUT_FOR_DELIVERY, true)).containsExactlyInAnyOrder(DELIVERED, FAILED);
        assertThat(ParcelLifecycle.nextStatuses(DELIVERED, true)).isEmpty();
    }
}
