package com.swiftdrop.delivery.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import com.swiftdrop.delivery.domain.OutboxEvent;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.infrastructure.outbox.OutboxPublisher.Result;

/** Relay ordering, blocking and crash windows of ADR 0004 §11, with a mocked publisher and repository. */
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final UUID PARCEL_A = UUID.randomUUID();
    private static final UUID PARCEL_B = UUID.randomUUID();

    private OutboxEventRepository outbox;
    private OutboxPublisher publisher;
    private SimpleMeterRegistry registry;
    private OutboxRelay relay;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        outbox = mock(OutboxEventRepository.class);
        publisher = mock(OutboxPublisher.class);
        registry = new SimpleMeterRegistry();
        relay = relay(TransactionOperations.withoutTransaction());
        when(outbox.tryAdvisoryTransactionLock(OutboxRelay.LOCK_KEY)).thenReturn(true);
        when(outbox.findFirstByPublishedAtIsNullOrderByIdAsc()).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Without the advisory lock the poll does nothing")
    void lockHeldElsewhere() {
        when(outbox.tryAdvisoryTransactionLock(OutboxRelay.LOCK_KEY)).thenReturn(false);

        assertThat(relay.pollOnce()).isEqualTo(OutboxRelay.PollResult.SKIPPED);
        verify(outbox, never()).findByPublishedAtIsNullOrderByIdAsc(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("Rows are published in outbox order and marked published")
    void publishesInOrder() {
        OutboxEvent a1 = row(PARCEL_A, 1);
        OutboxEvent b1 = row(PARCEL_B, 1);
        OutboxEvent a2 = row(PARCEL_A, 2);
        givenUnpublished(a1, b1, a2);
        when(publisher.publish(any())).thenReturn(Result.CONFIRMED);

        var result = relay.pollOnce();

        assertThat(result.published()).isEqualTo(3);
        assertThat(publishedOrder()).containsExactly(a1, b1, a2);
        assertThat(List.of(a1, b1, a2)).allMatch(OutboxEvent::isPublished);
    }

    @Test
    @DisplayName("A refused or unroutable row blocks only its own parcel; other parcels continue")
    void failingRowBlocksOnlyItsParcel() {
        OutboxEvent a1 = row(PARCEL_A, 1);
        OutboxEvent b1 = row(PARCEL_B, 1);
        OutboxEvent a2 = row(PARCEL_A, 2);
        OutboxEvent b2 = row(PARCEL_B, 2);
        givenUnpublished(a1, b1, a2, b2);
        when(publisher.publish(a1)).thenReturn(Result.NACKED);
        when(publisher.publish(b1)).thenReturn(Result.CONFIRMED);
        when(publisher.publish(b2)).thenReturn(Result.RETURNED);

        var result = relay.pollOnce();

        assertThat(publishedOrder()).containsExactly(a1, b1, b2);
        assertThat(result.published()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.stopped()).isFalse();
        assertThat(a1.isPublished()).isFalse();
        assertThat(a1.getAttempts()).isEqualTo(1);
        assertThat(a1.getLastFailureCategory()).isEqualTo("NACKED");
        assertThat(a1.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(b2.getLastFailureCategory()).isEqualTo("RETURNED");
        assertThat(a2.isPublished()).isFalse();
        assertThat(a2.getAttempts()).isZero();
    }

    @Test
    @DisplayName("A row waiting for its retry time holds back its parcel's later rows")
    void waitingRowBlocksItsParcel() {
        OutboxEvent a1 = row(PARCEL_A, 1);
        a1.recordFailure("NACKED", NOW.minusSeconds(1), Duration.ofSeconds(30));
        OutboxEvent a2 = row(PARCEL_A, 2);
        OutboxEvent b1 = row(PARCEL_B, 1);
        givenUnpublished(a1, a2, b1);
        when(publisher.publish(any())).thenReturn(Result.CONFIRMED);

        relay.pollOnce();

        assertThat(publishedOrder()).containsExactly(b1);
    }

    @Test
    @DisplayName("A broker failure ends the poll at once and records the attempt")
    void brokerFailureStopsPoll() {
        OutboxEvent a1 = row(PARCEL_A, 1);
        OutboxEvent b1 = row(PARCEL_B, 1);
        givenUnpublished(a1, b1);
        when(publisher.publish(a1)).thenThrow(new AmqpConnectException(new java.net.ConnectException("refused")));

        var result = relay.pollOnce();

        assertThat(result.stopped()).isTrue();
        assertThat(publishedOrder()).containsExactly(a1);
        assertThat(a1.getLastFailureCategory()).isEqualTo("BROKER_UNAVAILABLE");
        assertThat(b1.getAttempts()).isZero();
    }

    @Test
    @DisplayName("A confirm timeout ends the poll and leaves the row for a later retry")
    void confirmTimeoutStopsPoll() {
        OutboxEvent a1 = row(PARCEL_A, 1);
        OutboxEvent b1 = row(PARCEL_B, 1);
        givenUnpublished(a1, b1);
        when(publisher.publish(a1)).thenReturn(Result.CONFIRM_TIMEOUT);

        var result = relay.pollOnce();

        assertThat(result.stopped()).isTrue();
        assertThat(publishedOrder()).containsExactly(a1);
        assertThat(a1.isPublished()).isFalse();
        assertThat(a1.getNextAttemptAt()).isAfter(NOW);
    }

    @Test
    @DisplayName("Crash after the confirm, before the mark commits: the next poll publishes the same event ID again")
    void crashAfterConfirmRepublishesSameEventId() {
        UUID eventId = UUID.randomUUID();
        // Each poll reads the row afresh from the database, as after a rollback.
        when(outbox.findByPublishedAtIsNullOrderByIdAsc(any(Limit.class)))
                .thenAnswer(invocation -> List.of(row(PARCEL_A, 1, eventId)));
        when(publisher.publish(any())).thenReturn(Result.CONFIRMED);
        TransactionOperations commitFailsOnce = new TransactionOperations() {
            private boolean failed;

            @Override
            public <T> T execute(TransactionCallback<T> action) {
                T result = action.doInTransaction(null);
                if (!failed) {
                    failed = true;
                    throw new IllegalStateException("commit failed");
                }
                return result;
            }
        };
        OutboxRelay crashing = relay(commitFailsOnce);

        crashing.poll(); // the confirm arrived, but the mark was never committed
        crashing.poll();

        ArgumentCaptor<OutboxEvent> published = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(publisher, times(2)).publish(published.capture());
        assertThat(published.getAllValues()).extracting(OutboxEvent::getEventId).containsExactly(eventId, eventId);
    }

    @Test
    @DisplayName("Backoff doubles from 1 s and is capped at the configured maximum")
    void backoff() {
        assertThat(relay.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(relay.backoff(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(relay.backoff(6)).isEqualTo(Duration.ofSeconds(32));
        assertThat(relay.backoff(7)).isEqualTo(Duration.ofMinutes(1));
        assertThat(relay.backoff(100)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("Each poll refreshes the backlog metrics and the time of the last completed poll")
    void metrics() {
        OutboxEvent waiting = row(PARCEL_A, 1);
        ReflectionTestUtils.setField(waiting, "createdAt", NOW.minusSeconds(150));
        givenUnpublished();
        when(outbox.countByPublishedAtIsNull()).thenReturn(4L);
        when(outbox.findFirstByPublishedAtIsNullOrderByIdAsc()).thenReturn(Optional.of(waiting));
        when(outbox.countByPublishedAtIsNullAndAttemptsGreaterThanEqual(anyInt())).thenReturn(1L);

        relay.pollOnce();

        assertThat(registry.get("swiftdrop.outbox.unpublished").gauge().value()).isEqualTo(4);
        assertThat(registry.get("swiftdrop.outbox.oldest.unpublished.age").gauge().value()).isEqualTo(150);
        assertThat(registry.get("swiftdrop.outbox.attempts.exceeded").gauge().value()).isEqualTo(1);
        assertThat(registry.get("swiftdrop.outbox.last.poll").gauge().value()).isEqualTo(NOW.getEpochSecond());
    }

    private OutboxRelay relay(TransactionOperations transactions) {
        var properties = new OutboxRelayProperties(null, null, null, null, null, null, null, null);
        return new OutboxRelay(outbox, publisher, transactions, new OutboxRelayMetrics(registry), properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void givenUnpublished(OutboxEvent... rows) {
        when(outbox.findByPublishedAtIsNullOrderByIdAsc(any(Limit.class))).thenReturn(List.of(rows));
    }

    private List<OutboxEvent> publishedOrder() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(publisher, org.mockito.Mockito.atLeast(0)).publish(captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }

    private OutboxEvent row(UUID parcelId, int sequence) {
        return row(parcelId, sequence, UUID.randomUUID());
    }

    private OutboxEvent row(UUID parcelId, int sequence, UUID eventId) {
        OutboxEvent row = new OutboxEvent(eventId, parcelId, sequence, "parcel.status-changed", "{}",
                NOW.minusSeconds(5));
        ReflectionTestUtils.setField(row, "id", nextId++);
        return row;
    }
}
