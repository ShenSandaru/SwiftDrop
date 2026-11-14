package com.swiftdrop.merchant.application;

import static com.swiftdrop.merchant.application.MerchantEventFixture.NOW;
import static com.swiftdrop.merchant.application.MerchantEventFixture.PARCEL_ID;
import static com.swiftdrop.merchant.application.MerchantEventFixture.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;

/** Gap reconciliation from delivery's history (ADR 0004 §4.3) and the review's contiguity rule. */
class MerchantEventReconcilerTest {

    private static final UUID ENDPOINT = UUID.fromString("e0000000-0000-0000-0000-00000000000a");

    private final MerchantEventFixture f = new MerchantEventFixture(ENDPOINT);
    private final MerchantEventProcessor processor = f.processor();

    private MerchantEventReconciler reconcilerAt(java.time.Instant now) {
        return new MerchantEventReconciler(f.states, f.pendingRepository, f.processedRepository, f.history, processor,
                f.json, f.metrics, f.properties, new TransactionTemplate(mock(PlatformTransactionManager.class)),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    /** The pending event's body as received: valid JSON with its own spacing, so a re-serialization would differ. */
    private String fifthBody(ParcelStatusChanged fifth) {
        return f.json.toJson(fifth).replace(",", ", ");
    }

    /** Sequence 1 applied, sequence 5 pending: the gap is 2 to 4. */
    private ParcelStatusChanged gapOneToFive() {
        processor.process(event(1), "{}");
        ParcelStatusChanged fifth = event(5);
        processor.process(fifth, fifthBody(fifth));
        when(f.pendingRepository.findDueParcelIds(any(), any())).thenReturn(List.of(PARCEL_ID));
        return fifth;
    }

    @Test
    @DisplayName("After the grace period the missing events are applied from history with their original IDs")
    void reconcilesFromHistory() {
        ParcelStatusChanged fifth = gapOneToFive();
        List<ParcelStatusChanged> missing = List.of(event(4), event(2), event(3));
        when(f.history.eventsBetween(PARCEL_ID, 2, 4)).thenReturn(missing);

        reconcilerAt(NOW.plusSeconds(61)).reconcileDue();

        assertThat(f.state.getLastAppliedSequence()).isEqualTo(5);
        assertThat(f.pending).isEmpty();
        assertThat(f.processed).containsKeys(missing.get(0).eventId(), missing.get(1).eventId(), missing.get(2).eventId());
        assertThat(f.records).extracting(r -> r[2]).containsExactly("CREATED", "PICKED_UP", "AT_HUB", "IN_TRANSIT",
                "OUT_FOR_DELIVERY");
        // A reconciled event's payload is its envelope as JSON; the pending event keeps its received body
        assertThat((String) f.records.get(1)[3]).contains(missing.get(1).eventId().toString());
        assertThat(f.records.get(4)[3]).isEqualTo(fifthBody(fifth));
    }

    @Test
    @DisplayName("The late original of a reconciled event is then a duplicate")
    void lateOriginalIsDuplicate() {
        gapOneToFive();
        ParcelStatusChanged second = event(2);
        when(f.history.eventsBetween(PARCEL_ID, 2, 4)).thenReturn(List.of(second, event(3), event(4)));
        reconcilerAt(NOW.plusSeconds(61)).reconcileDue();

        assertThat(processor.process(second, "{}")).isEqualTo(MerchantEventProcessor.Outcome.DUPLICATE);
        assertThat(f.records).hasSize(5);
    }

    @Test
    @DisplayName("Non-contiguous history: only the contiguous prefix is applied, the state never skips a sequence")
    void contiguousPrefixOnly() {
        ParcelStatusChanged fifth = gapOneToFive();
        when(f.history.eventsBetween(PARCEL_ID, 2, 4)).thenReturn(List.of(event(2), event(4)));

        reconcilerAt(NOW.plusSeconds(61)).reconcileDue();

        assertThat(f.state.getLastAppliedSequence()).isEqualTo(2);
        assertThat(f.pending).containsOnlyKeys(fifth.eventId());
        assertThat(f.pending.get(fifth.eventId()).getReconcileAttempts()).isEqualTo(1);
        assertThat(f.pending.get(fifth.eventId()).getNextReconcileAt()).isEqualTo(NOW.plusSeconds(61).plusSeconds(60));
        assertThat(f.records).extracting(r -> r[2]).containsExactly("CREATED", "PICKED_UP");
    }

    @Test
    @DisplayName("A rebuilt event of another parcel or merchant is not applied")
    void foreignHistoryRejected() {
        gapOneToFive();
        ParcelStatusChanged foreign = ParcelStatusChanged.of(UUID.randomUUID(), NOW,
                new ParcelStatusChangedData(PARCEL_ID, "SD7K3M9Q2XWB", ParcelStatus.CREATED, ParcelStatus.PICKED_UP, 2,
                        ParcelSource.MERCHANT, null, UUID.randomUUID(), "ORD-10045", null));
        when(f.history.eventsBetween(PARCEL_ID, 2, 4)).thenReturn(List.of(foreign, event(3), event(4)));

        reconcilerAt(NOW.plusSeconds(61)).reconcileDue();

        assertThat(f.state.getLastAppliedSequence()).isEqualTo(1);
        assertThat(f.pending).hasSize(1);
    }

    @Test
    @DisplayName("Before the grace period nothing is reconciled")
    void notDueYet() {
        gapOneToFive();

        reconcilerAt(NOW.plusSeconds(30)).reconcileDue();

        verify(f.history, never()).eventsBetween(any(), anyInt(), anyInt());
        assertThat(f.state.getLastAppliedSequence()).isEqualTo(1);
    }

    @Test
    @DisplayName("A parcel locked by another instance is skipped")
    void busyParcelSkipped() {
        gapOneToFive();
        when(f.states.tryLockByParcelId(PARCEL_ID)).thenReturn(java.util.Optional.empty());

        reconcilerAt(NOW.plusSeconds(61)).reconcileDue();

        verify(f.history, never()).eventsBetween(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Failing history keeps the pending event, backs off, and marks the parcel stuck after 5 attempts")
    void stuckAfterRepeatedFailures() {
        ParcelStatusChanged fifth = gapOneToFive();
        when(f.history.eventsBetween(eq(PARCEL_ID), anyInt(), anyInt()))
                .thenThrow(new ParcelNotFoundException());

        java.time.Instant now = NOW.plusSeconds(61);
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(f.state.isStuck()).isFalse();
            reconcilerAt(now).reconcileDue();
            now = f.pending.get(fifth.eventId()).getNextReconcileAt();
        }

        assertThat(f.state.isStuck()).isTrue();
        assertThat(f.pending).containsOnlyKeys(fifth.eventId());
        assertThat(f.pending.get(fifth.eventId()).getReconcileAttempts()).isEqualTo(5);
        assertThat(f.state.getLastAppliedSequence()).isEqualTo(1);
        assertThat(f.counter("swiftdrop.merchant.events.stuck")).isEqualTo(1);

        // A stuck parcel is left alone until someone resets it
        reconcilerAt(now.plus(Duration.ofHours(2))).reconcileDue();
        verify(f.history, org.mockito.Mockito.times(5)).eventsBetween(any(), anyInt(), anyInt());
    }
}
