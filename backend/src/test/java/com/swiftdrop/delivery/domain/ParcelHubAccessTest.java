package com.swiftdrop.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.pricing.api.ParcelSize;

/** Which hub acts on a parcel and which hubs may only follow it (ADR 0012 §3). */
class ParcelHubAccessTest {

    private static final Instant NOW = Instant.parse("2026-11-14T08:00:00Z");
    private static final UUID BADULLA = UUID.randomUUID();
    private static final UUID COLOMBO = UUID.randomUUID();
    private static final UUID KANDY = UUID.randomUUID();
    private static final UUID GALLE = UUID.randomUUID();

    private static HubScope scope(UUID... hubs) {
        return HubScope.of(Set.of(hubs));
    }

    /** A Badulla to Kandy parcel through Colombo, in the given state. */
    private static Parcel parcel(ParcelStatus status, UUID current, UUID next) {
        Parcel parcel = Parcel.forMerchant("SD7K3M9Q2XWB", UUID.randomUUID(), "ORD-1",
                new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Badulla", UUID.randomUUID(),
                        "90000"),
                new ParcelAddress("Kumari Silva", "+94711234567", "4 Lake View", null, "Kandy", UUID.randomUUID(),
                        "20000"),
                ParcelSize.SMALL, 1000, "Books", new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()),
                NOW);
        ReflectionTestUtils.setField(parcel, "status", status);
        if (status != ParcelStatus.CREATED && status != ParcelStatus.CANCELLED) {
            ReflectionTestUtils.setField(parcel, "originHubId", BADULLA);
            ReflectionTestUtils.setField(parcel, "viaHubId", COLOMBO);
            ReflectionTestUtils.setField(parcel, "destinationHubId", KANDY);
        }
        ReflectionTestUtils.setField(parcel, "currentHubId", current);
        ReflectionTestUtils.setField(parcel, "nextHubId", next);
        return parcel;
    }

    @Test
    void thePickupBranchActsUntilPickup() {
        Parcel parcel = parcel(ParcelStatus.CREATED, null, null);

        assertThat(parcel.accessFor(scope(BADULLA), BADULLA)).isEqualTo(HubAccess.ACT);
        assertThat(parcel.accessFor(scope(KANDY), BADULLA)).isEqualTo(HubAccess.NONE);
        assertThat(parcel.accessFor(scope(COLOMBO), BADULLA)).isEqualTo(HubAccess.NONE);
    }

    @Test
    void theOriginHubActsAfterPickupAndAtItsOwnHub() {
        assertThat(parcel(ParcelStatus.PICKED_UP, null, null).accessFor(scope(BADULLA), BADULLA))
                .isEqualTo(HubAccess.ACT);
        assertThat(parcel(ParcelStatus.AT_HUB, BADULLA, null).accessFor(scope(BADULLA), BADULLA))
                .isEqualTo(HubAccess.ACT);
    }

    @Test
    void theHubItTravelsToActsAndTheSendingHubOnlyFollows() {
        Parcel onTheTruck = parcel(ParcelStatus.IN_TRANSIT, null, COLOMBO);

        assertThat(onTheTruck.accessFor(scope(COLOMBO), BADULLA)).isEqualTo(HubAccess.ACT);
        assertThat(onTheTruck.accessFor(scope(BADULLA), BADULLA)).isEqualTo(HubAccess.READ);
        assertThat(onTheTruck.accessFor(scope(KANDY), BADULLA)).isEqualTo(HubAccess.READ);
        assertThat(onTheTruck.accessFor(scope(GALLE), BADULLA)).isEqualTo(HubAccess.NONE);
    }

    @Test
    void theHubItIsAtActsAndTheOthersOnTheRouteFollow() {
        Parcel atTheCentralHub = parcel(ParcelStatus.AT_HUB, COLOMBO, null);

        assertThat(atTheCentralHub.accessFor(scope(COLOMBO), BADULLA)).isEqualTo(HubAccess.ACT);
        assertThat(atTheCentralHub.accessFor(scope(BADULLA), BADULLA)).isEqualTo(HubAccess.READ);
        assertThat(atTheCentralHub.accessFor(scope(KANDY), BADULLA)).isEqualTo(HubAccess.READ);
    }

    @Test
    void theDestinationHubHandlesEveryDeliveryState() {
        for (ParcelStatus status : new ParcelStatus[] {ParcelStatus.OUT_FOR_DELIVERY,
                ParcelStatus.DELIVERY_ATTEMPT_FAILED, ParcelStatus.RETURNING}) {
            Parcel parcel = parcel(status, null, null);

            assertThat(parcel.accessFor(scope(KANDY), BADULLA)).as("%s at the destination", status)
                    .isEqualTo(HubAccess.ACT);
            assertThat(parcel.accessFor(scope(BADULLA), BADULLA)).as("%s at the origin", status)
                    .isEqualTo(HubAccess.READ);
        }
    }

    @Test
    void finishedParcelsCanOnlyBeReadByTheHubsOnTheirRoute() {
        for (ParcelStatus status : new ParcelStatus[] {ParcelStatus.DELIVERED, ParcelStatus.RETURNED,
                ParcelStatus.FAILED}) {
            Parcel parcel = parcel(status, null, null);

            assertThat(parcel.responsibleHubId(BADULLA)).isNull();
            assertThat(parcel.accessFor(scope(BADULLA), BADULLA)).as("%s", status).isEqualTo(HubAccess.READ);
            assertThat(parcel.accessFor(scope(COLOMBO), BADULLA)).as("%s", status).isEqualTo(HubAccess.READ);
            assertThat(parcel.accessFor(scope(GALLE), BADULLA)).as("%s", status).isEqualTo(HubAccess.NONE);
        }
    }

    @Test
    void aParcelCancelledBeforePickupBelongsToItsPickupBranch() {
        Parcel parcel = parcel(ParcelStatus.CANCELLED, null, null);

        assertThat(parcel.accessFor(scope(BADULLA), BADULLA)).isEqualTo(HubAccess.READ);
        assertThat(parcel.accessFor(scope(KANDY), BADULLA)).isEqualTo(HubAccess.NONE);
    }

    @Test
    void parcelsPickedUpWithoutARouteAreVisibleOnlyToStaffWhoWorkEverywhere() {
        Parcel legacy = parcel(ParcelStatus.IN_TRANSIT, null, null);
        ReflectionTestUtils.setField(legacy, "originHubId", null);
        ReflectionTestUtils.setField(legacy, "viaHubId", null);
        ReflectionTestUtils.setField(legacy, "destinationHubId", null);

        assertThat(legacy.accessFor(scope(BADULLA, COLOMBO, KANDY), BADULLA)).isEqualTo(HubAccess.NONE);
        assertThat(legacy.accessFor(HubScope.EVERYWHERE, BADULLA)).isEqualTo(HubAccess.ACT);
    }

    @Test
    void staffWhoWorkEverywhereActOnAnything() {
        for (ParcelStatus status : ParcelStatus.values()) {
            assertThat(parcel(status, null, null).accessFor(HubScope.EVERYWHERE, BADULLA)).as("%s", status)
                    .isEqualTo(HubAccess.ACT);
        }
    }

    @Test
    void staffWithNoHubsSeeNothing() {
        for (ParcelStatus status : ParcelStatus.values()) {
            assertThat(parcel(status, null, null).accessFor(HubScope.NONE, BADULLA)).as("%s", status)
                    .isEqualTo(HubAccess.NONE);
        }
        assertThat(HubScope.of(Set.of())).isSameAs(HubScope.NONE);
        assertThat(HubScope.NONE.isEmpty()).isTrue();
    }

    @Test
    void severalHubsAddUp() {
        Parcel onTheTruck = parcel(ParcelStatus.IN_TRANSIT, null, COLOMBO);

        assertThat(onTheTruck.accessFor(scope(BADULLA, COLOMBO), BADULLA)).isEqualTo(HubAccess.ACT);
    }

    @Test
    void aPickupZoneWithoutAHubIsNobodysToAct() {
        Parcel parcel = parcel(ParcelStatus.CREATED, null, null);

        assertThat(parcel.accessFor(scope(BADULLA), null)).isEqualTo(HubAccess.NONE);
    }
}
