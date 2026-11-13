package com.swiftdrop.delivery.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.domain.OutboxEvent;
import com.swiftdrop.delivery.domain.OutboxEventRepository;

/** Relay ordering, blocking and crash windows (ADR 0004 §4.2, §11) with a mocked broker. */
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:15:30Z");
    private static final UUID PARCEL_A = UUID.randomUUID();
    private static final UUID PARCEL_B = UUID.randomUUID();

    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    private final OutboxPublisher publisher = mock(OutboxPublisher.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final Map<UUID, PublishOutcome> outcomes = new HashMap<>();
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outbox, publisher, TransactionOperations.withoutTransaction(),
                OutboxRelayProperties.defaults(), clock);
        when(outbox.tryRelayLock(OutboxRelay.LOCK_KEY)).thenReturn(true);
        when(publisher.publish(any())).thenAnswer(invocation -> {
            OutboxEvent row = invocation.getArgument(0);
            return outcomes.getOrDefault(row.getEventId(), PublishOutcome.CONFIRMED);
        });
    }

    private static OutboxEvent row(UUID parcelId, int sequence) {
        return new OutboxEvent(UUID.randomUUID(), parcelId, sequence, ParcelStatusChanged.EVENT_TYPE, "{}",
                NOW.minusSeconds(5));
    }

    private void unpublished(OutboxEvent... rows) {
        when(outbox.findByPublishedAtIsNullOrderByIdAsc(any(Limit.class))).thenReturn(List.of(rows));
    }

    private List<UUID> publishedEventIds(int times) {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(publisher, org.mockito.Mockito.times(times)).publish(captor.capture());
        return captor.getAllValues().stream().map(OutboxEvent::getEventId).toList();
    }

    @Test
    void rowsArePublishedInOutboxOrderAndMarked() {
        OutboxEvent a1 = row(PARCEL_A, 1);
        OutboxEvent b1 = row(PARCEL_B, 1);
        OutboxEvent a2 = row(PARCEL_A, 2);
        unpublished(a1, b1, a2);

        relay.poll();

        InOrder order = inOrder(publisher);
        order.verify(publisher).publish(a1);
        order.verify(publisher).publish(b1);
        order.verify(publisher).publish(a2);
        assertThat(List.of(a1, b1, a2)).allSatisfy(row -> assertThat(row.getPublishedAt()).isEqualTo(NOW));
        assertThat(relay.lastCompletedPoll()).isEqualTo(NOW);
    }

    @Test
    void batchSizeLimitsTheRowsRead() {
        unpublished();

        relay.poll();

        verify(outbox).findByPublishedAtIsNullOrderByIdAsc(Limit.of(100));
    }

    @Test
    void failingRowBlocksOnlyItsOwnParcel() {
        OutboxEvent a1 = row(PARCEL_A, 1);
        OutboxEvent b1 = row(PARCEL_B, 1);
        OutboxEvent a2 = row(PARCEL_A, 2);
        OutboxEvent b2 = row(PARCEL_B, 2);
        unpublished(a1, b1, a2, b2);
        outcomes.put(a1.getEventId(), PublishOutcome.RETURNED);

        relay.poll();

        verify(publisher, never()).publish(a2);
        assertThat(a1.isPublished()).isFalse();
        assertThat(a1.getAttempts()).isEqualTo(1);
        assertThat(a1.getLastFailureCategory()).isEqualTo("returned");
        assertThat(a1.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(a2.isPublished()).isFalse();
        assertThat(a2.getAttempts()).isZero();
        assertThat(b1.isPublished()).isTrue();
        assertThat(b2.isPublished()).isTrue();
    }

    @Test
    void negativeConfirmIsRetriedLaterWithoutEndingThePoll() {
        OutboxEvent a1 = row(PARCEL_A, 1);
        OutboxEvent b1 = row(PARCEL_B, 1);
        unpublished(a1, b1);
        outcomes.put(a1.getEventId(), PublishOutcome.NACKED);

        relay.poll();

        assertThat(a1.getLastFailureCategory()).isEqualTo("nack");
        assertThat(a1.isPublished()).isFalse();
        assertThat(b1.isPublished()).isTrue();
    }

    @Test
    void rowWaitingForItsRetryBlocksItsParcelButIsNotPublished() {
        OutboxEvent a1 = row(PARCEL_A, 1);
        a1.recordFailure("returned", NOW.plusSeconds(30));
        OutboxEvent a2 = row(PARCEL_A, 2);
        OutboxEvent b1 = row(PARCEL_B, 1);
        unpublished(a1, a2, b1);

        relay.poll();

        verify(publisher, never()).publish(a1);
        verify(publisher, never()).publish(a2);
        assertThat(a1.getAttempts()).isEqualTo(1);
        assertThat(b1.isPublished()).isTrue();
    }

    @Test
    void brokerUnavailableEndsThePoll() {
        OutboxEvent a1 = row(PARCEL_A, 1);
        OutboxEvent b1 = row(PARCEL_B, 1);
        unpublished(a1, b1);
        outcomes.put(a1.getEventId(), PublishOutcome.BROKER_UNAVAILABLE);

        relay.poll();

        verify(publisher, never()).publish(b1);
        assertThat(a1.getLastFailureCategory()).isEqualTo("broker-unavailable");
        assertThat(b1.getAttempts()).isZero();
        assertThat(relay.lastCompletedPoll()).isEqualTo(NOW);
    }

    @Test
    void confirmTimeoutEndsThePoll() {
        OutboxEvent a1 = row(PARCEL_A, 1);
        OutboxEvent b1 = row(PARCEL_B, 1);
        unpublished(a1, b1);
        outcomes.put(a1.getEventId(), PublishOutcome.CONFIRM_TIMEOUT);

        relay.poll();

        verify(publisher, never()).publish(b1);
        assertThat(a1.getLastFailureCategory()).isEqualTo("confirm-timeout");
        assertThat(a1.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void backoffGrowsWithEachFailedAttempt() {
        OutboxEvent a1 = row(PARCEL_A, 1);
        a1.recordFailure("returned", NOW);
        a1.recordFailure("returned", NOW);
        a1.recordFailure("returned", NOW);
        unpublished(a1);
        outcomes.put(a1.getEventId(), PublishOutcome.RETURNED);

        relay.poll();

        assertThat(a1.getAttempts()).isEqualTo(4);
        assertThat(a1.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofSeconds(8)));
    }

    @Test
    void secondRelayDoesNotRunWhileTheLockIsHeld() {
        when(outbox.tryRelayLock(OutboxRelay.LOCK_KEY)).thenReturn(false);

        relay.poll();

        verify(outbox, never()).findByPublishedAtIsNullOrderByIdAsc(any(Limit.class));
        verify(publisher, never()).publish(any());
        assertThat(relay.lastCompletedPoll()).isNull();
    }

    @Test
    void crashBeforePublishingLeavesTheRowUnpublished() {
        OutboxEvent a1 = row(PARCEL_A, 1);
        unpublished(a1);
        when(outbox.tryRelayLock(anyLong())).thenThrow(new IllegalStateException("database unavailable"));

        relay.poll();

        verify(publisher, never()).publish(any());
        assertThat(a1.isPublished()).isFalse();
        assertThat(a1.getAttempts()).isZero();
        assertThat(relay.lastCompletedPoll()).isNull();
    }

    @Test
    void confirmFollowedByAFailedCommitPublishesTheSameEventIdAgain() {
        UUID eventId = UUID.randomUUID();
        List<TransactionCallback<?>> calls = new ArrayList<>();
        // First poll: the broker confirms, then the commit that would store the mark fails
        TransactionOperations commitFails = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                calls.add(action);
                action.doInTransaction(null);
                throw new IllegalStateException("commit failed");
            }
        };
        OutboxRelay failingCommit = new OutboxRelay(outbox, publisher, commitFails, OutboxRelayProperties.defaults(),
                clock);
        unpublished(new OutboxEvent(eventId, PARCEL_A, 1, ParcelStatusChanged.EVENT_TYPE, "{}", NOW));

        failingCommit.poll();

        // After the rollback the database still holds the row unpublished, so the next poll reads it again
        unpublished(new OutboxEvent(eventId, PARCEL_A, 1, ParcelStatusChanged.EVENT_TYPE, "{}", NOW));
        relay.poll();

        assertThat(calls).hasSize(1);
        assertThat(failingCommit.lastCompletedPoll()).isNull();
        assertThat(publishedEventIds(2)).containsExactly(eventId, eventId);
    }
}
