package com.swiftdrop.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelStatus;

class ParcelStatusHistoryEntryTest {

    private static final UUID PARCEL_ID = UUID.randomUUID();
    private static final UUID COURIER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30Z");

    @Test
    void creationEntryIsSequenceOneFromNothingToCreated() {
        ParcelStatusHistoryEntry entry = entry(1, null, ParcelStatus.CREATED, null, null);

        assertThat(entry.getSequence()).isEqualTo(1);
        assertThat(entry.getFromStatus()).isNull();
    }

    @Test
    void onlySequenceOneMayBeTheCreationEntry() {
        assertThatThrownBy(() -> entry(2, null, ParcelStatus.CREATED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entry(1, ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entry(0, null, ParcelStatus.CREATED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedRequiresReasonAndOtherStatusesRejectOne() {
        assertThat(entry(4, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED, FailureReason.REFUSED, null)
                .getFailureReasonCode()).isEqualTo(FailureReason.REFUSED);
        assertThatThrownBy(() -> entry(4, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entry(3, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, FailureReason.OTHER, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noteIsTrimmedBlankBecomesNullAndLengthIsLimited() {
        assertThat(entry(2, ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, "  Gate 2  ").getNote())
                .isEqualTo("Gate 2");
        assertThat(entry(2, ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, "  ").getNote()).isNull();
        assertThatThrownBy(() -> entry(2, ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, "x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNeverContainsTheNote() {
        assertThat(entry(2, ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, "Call Nimal at +94771234567")
                .toString()).doesNotContain("Nimal", "+9477");
    }

    private static ParcelStatusHistoryEntry entry(int sequence, ParcelStatus from, ParcelStatus to,
            FailureReason reason, String note) {
        return new ParcelStatusHistoryEntry(PARCEL_ID, sequence, from, to, COURIER_ID, ActorRole.COURIER, note,
                reason, UUID.randomUUID(), NOW);
    }
}
