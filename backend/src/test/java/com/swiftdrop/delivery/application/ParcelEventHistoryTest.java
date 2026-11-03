package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryEntry;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.pricing.api.ParcelSize;

class ParcelEventHistoryTest {

    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30Z");

    private ParcelRepository parcels;
    private ParcelStatusHistoryRepository history;
    private ParcelEventHistory events;
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        parcels = mock(ParcelRepository.class);
        history = mock(ParcelStatusHistoryRepository.class);
        events = new ParcelEventHistory(parcels, history);
        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null);
        parcel = Parcel.forCustomer("SD7K3M9Q2XWB", CUSTOMER_ID, address, address, ParcelSize.SMALL, 1000, null,
                new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), NOW);
        ReflectionTestUtils.setField(parcel, "id", PARCEL_ID);
        when(parcels.findById(PARCEL_ID)).thenReturn(Optional.of(parcel));
    }

    @Test
    void rebuildsEventsWithTheirOriginalIdsInSequenceOrder() {
        ParcelStatusHistoryEntry created = entry(1, null, ParcelStatus.CREATED);
        ParcelStatusHistoryEntry pickedUp = entry(2, ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
        when(history.findByParcelIdAndSequenceBetweenOrderBySequenceAsc(PARCEL_ID, 1, 2))
                .thenReturn(List.of(created, pickedUp));

        List<ParcelStatusChanged> rebuilt = events.eventsBetween(PARCEL_ID, 1, 2);

        assertThat(rebuilt).extracting(ParcelStatusChanged::eventId)
                .containsExactly(created.getEventId(), pickedUp.getEventId());
        assertThat(rebuilt).extracting(event -> event.data().sequence()).containsExactly(1, 2);
        assertThat(rebuilt.get(1)).isEqualTo(ParcelEventRecorder.eventFor(parcel, pickedUp));
        assertThat(rebuilt.get(1).occurredAt()).isEqualTo(pickedUp.getOccurredAt());
    }

    @Test
    void sequencesThatDoNotExistYetAreSimplyAbsent() {
        when(history.findByParcelIdAndSequenceBetweenOrderBySequenceAsc(PARCEL_ID, 3, 9)).thenReturn(List.of());

        assertThat(events.eventsBetween(PARCEL_ID, 3, 9)).isEmpty();
    }

    @Test
    void unknownParcelIsNotFound() {
        when(parcels.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> events.eventsBetween(UUID.randomUUID(), 1, 1))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void invalidRangesAreRejectedBeforeAnyLookup() {
        assertThatThrownBy(() -> events.eventsBetween(PARCEL_ID, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> events.eventsBetween(PARCEL_ID, 3, 2)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(parcels, history);
    }

    private static ParcelStatusHistoryEntry entry(int sequence, ParcelStatus from, ParcelStatus to) {
        return new ParcelStatusHistoryEntry(PARCEL_ID, sequence, from, to, CUSTOMER_ID, ActorRole.CUSTOMER, null, null,
                UUID.randomUUID(), NOW.plusSeconds(sequence));
    }
}
