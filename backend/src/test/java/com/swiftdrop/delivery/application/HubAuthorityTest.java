package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelForbiddenException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.Staff;
import com.swiftdrop.delivery.domain.HubScope;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.pricing.api.ParcelSize;

/** Who may act on a parcel, and which couriers may be given it (ADR 0012 §3, §4). */
class HubAuthorityTest {

    private static final Instant NOW = Instant.parse("2026-11-14T08:00:00Z");
    private static final UUID BADULLA_ZONE = UUID.randomUUID();
    private static final UUID BADULLA = UUID.randomUUID();
    private static final UUID KANDY = UUID.randomUUID();
    private static final UUID DISPATCHER = UUID.randomUUID();
    private static final UUID COURIER = UUID.randomUUID();

    private final StaffScopes scopes = mock(StaffScopes.class);
    private final HubNetwork network = mock(HubNetwork.class);
    private final HubAuthority authority = new HubAuthority(scopes, network);
    private final Parcel parcel = newParcel();

    private Parcel newParcel() {
        Parcel created = Parcel.forMerchant("SD7K3M9Q2XWB", UUID.randomUUID(), "ORD-1",
                new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Badulla", BADULLA_ZONE,
                        "90000"),
                new ParcelAddress("Kumari Silva", "+94711234567", "4 Lake View", null, "Kandy", UUID.randomUUID(),
                        "20000"),
                ParcelSize.SMALL, 1000, "Books", new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()),
                NOW);
        when(network.branchHubId(BADULLA_ZONE)).thenReturn(Optional.of(BADULLA));
        return created;
    }

    @Test
    void aDispatcherActsOnParcelsOfTheirHubOnly() {
        when(scopes.scopeOf(DISPATCHER)).thenReturn(HubScope.of(Set.of(BADULLA)));
        assertThatCode(() -> authority.requireCanAct(parcel, Staff.dispatcher(DISPATCHER))).doesNotThrowAnyException();

        when(scopes.scopeOf(DISPATCHER)).thenReturn(HubScope.of(Set.of(KANDY)));
        assertThatThrownBy(() -> authority.requireCanAct(parcel, Staff.dispatcher(DISPATCHER)))
                .isInstanceOf(ParcelForbiddenException.class)
                .hasMessage("This parcel is handled by another hub");
    }

    @Test
    void aDispatcherWithNoHubsActsOnNothing() {
        when(scopes.scopeOf(DISPATCHER)).thenReturn(HubScope.NONE);

        assertThatThrownBy(() -> authority.requireCanAct(parcel, Staff.dispatcher(DISPATCHER)))
                .isInstanceOf(ParcelForbiddenException.class);
    }

    @Test
    void anAdminNeedsNoLookup() {
        assertThatCode(() -> authority.requireCanAct(parcel, Staff.admin(UUID.randomUUID()))).doesNotThrowAnyException();

        verifyNoInteractions(scopes);
    }

    @Test
    void aFollowingHubMayNotAct() {
        // On the truck from Badulla to Kandy: Badulla follows, Kandy acts
        ReflectionTestUtils.setField(parcel, "status", ParcelStatus.IN_TRANSIT);
        ReflectionTestUtils.setField(parcel, "originHubId", BADULLA);
        ReflectionTestUtils.setField(parcel, "destinationHubId", KANDY);
        ReflectionTestUtils.setField(parcel, "nextHubId", KANDY);
        when(scopes.scopeOf(DISPATCHER)).thenReturn(HubScope.of(Set.of(BADULLA)));

        assertThatThrownBy(() -> authority.requireCanAct(parcel, Staff.dispatcher(DISPATCHER)))
                .isInstanceOf(ParcelForbiddenException.class);
    }

    @Test
    void aCourierOfTheParcelsHubMayBeGivenIt() {
        when(scopes.scopeOf(COURIER)).thenReturn(HubScope.of(Set.of(BADULLA)));

        assertThatCode(() -> authority.requireCourierMayHandle(parcel, COURIER, HubScope.of(Set.of(BADULLA))))
                .doesNotThrowAnyException();
    }

    @Test
    void aCourierOfAnotherHubMayNotEvenForStaffWhoWorkEverywhere() {
        when(scopes.scopeOf(COURIER)).thenReturn(HubScope.of(Set.of(KANDY)));

        assertThatThrownBy(() -> authority.requireCourierMayHandle(parcel, COURIER, HubScope.EVERYWHERE))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("The courier does not work for this parcel's hub");
    }

    @Test
    void aCourierWithNoHubCanOnlyBeGivenAParcelByStaffWhoWorkEverywhere() {
        when(scopes.scopeOf(COURIER)).thenReturn(HubScope.NONE);

        assertThatCode(() -> authority.requireCourierMayHandle(parcel, COURIER, HubScope.EVERYWHERE))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authority.requireCourierMayHandle(parcel, COURIER, HubScope.of(Set.of(BADULLA))))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("The courier is not assigned to a hub");
    }
}
