package com.swiftdrop.delivery.domain;

import static com.swiftdrop.delivery.api.ParcelStatus.CREATED;
import static com.swiftdrop.delivery.api.ParcelStatus.DELIVERED;
import static com.swiftdrop.delivery.api.ParcelStatus.FAILED;
import static com.swiftdrop.delivery.api.ParcelStatus.IN_TRANSIT;
import static com.swiftdrop.delivery.api.ParcelStatus.OUT_FOR_DELIVERY;
import static com.swiftdrop.delivery.api.ParcelStatus.PICKED_UP;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.swiftdrop.delivery.api.ParcelStatus;

/** Every one of the 36 status pairs against the transition table of ADR 0006 §1. */
class ParcelLifecycleTest {

    private static final Set<String> ALLOWED = Set.of(
            "CREATED->PICKED_UP",
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

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void onlyTheAdrTransitionsAreAllowed(ParcelStatus from, ParcelStatus to) {
        assertThat(ParcelLifecycle.canMove(from, to)).isEqualTo(ALLOWED.contains(from + "->" + to));
    }

    @Test
    void deliveredAndFailedAreTheOnlyTerminalStatuses() {
        assertThat(Stream.of(ParcelStatus.values()).filter(ParcelLifecycle::isTerminal))
                .containsExactlyInAnyOrder(DELIVERED, FAILED);
    }

    @Test
    void nextStatusesMatchTheTable() {
        assertThat(ParcelLifecycle.nextStatuses(CREATED)).containsExactly(PICKED_UP);
        assertThat(ParcelLifecycle.nextStatuses(PICKED_UP)).containsExactlyInAnyOrder(IN_TRANSIT, FAILED);
        assertThat(ParcelLifecycle.nextStatuses(IN_TRANSIT)).containsExactlyInAnyOrder(OUT_FOR_DELIVERY, FAILED);
        assertThat(ParcelLifecycle.nextStatuses(OUT_FOR_DELIVERY)).containsExactlyInAnyOrder(DELIVERED, FAILED);
        assertThat(ParcelLifecycle.nextStatuses(DELIVERED)).isEmpty();
        assertThat(ParcelLifecycle.nextStatuses(FAILED)).isEmpty();
    }
}
