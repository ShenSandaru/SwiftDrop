package com.swiftdrop.merchant.application;

import static com.swiftdrop.merchant.application.MerchantEventFixture.MERCHANT_ID;
import static com.swiftdrop.merchant.application.MerchantEventFixture.NOW;
import static com.swiftdrop.merchant.application.MerchantEventFixture.PARCEL_ID;
import static com.swiftdrop.merchant.application.MerchantEventFixture.REFERENCE;
import static com.swiftdrop.merchant.application.MerchantEventFixture.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.merchant.application.MerchantEventProcessor.Outcome;
import com.swiftdrop.merchant.domain.MerchantEventAnomaly.Kind;
import com.swiftdrop.merchant.domain.MerchantEventState;

/** ADR 0004 §4.3 consumer rules and the SD-410 review cases, against in-memory stores. */
class MerchantEventProcessorTest {

    private static final UUID ENDPOINT_A = UUID.fromString("e0000000-0000-0000-0000-00000000000a");
    private static final UUID ENDPOINT_B = UUID.fromString("e0000000-0000-0000-0000-00000000000b");

    private final MerchantEventFixture f = new MerchantEventFixture(ENDPOINT_A, ENDPOINT_B);
    private final MerchantEventProcessor processor = f.processor();

    private Outcome process(ParcelStatusChanged event) {
        return processor.process(event, f.json.toJson(event));
    }

    @Test
    @DisplayName("The next sequence creates one PENDING record per subscribed endpoint, with the received body")
    void appliesInOrder() {
        ParcelStatusChanged created = event(1);
        String body = "{\"eventId\":\"" + created.eventId() + "\", \"exactly\": \"as received\"}";

        // The stored payload is the body as received, not a re-serialization
        assertThat(processor.process(created, body)).isEqualTo(Outcome.APPLIED);

        assertThat(f.records).hasSize(2);
        assertThat(f.records).extracting(r -> r[0]).containsExactlyInAnyOrder(ENDPOINT_A, ENDPOINT_B);
        assertThat(f.records).allSatisfy(r -> {
            assertThat(r[2]).isEqualTo("CREATED");
            assertThat(r[3]).isEqualTo(body);
        });
        assertThat(f.state.getLastAppliedSequence()).isEqualTo(1);
        assertThat(f.state.getMerchantId()).isEqualTo(MERCHANT_ID);
        assertThat(f.processed).containsKey(created.eventId());
    }

    @Test
    @DisplayName("Endpoints are looked up for the event's new status")
    void endpointsForTheNewStatus() {
        process(event(1));
        process(event(2));

        verify(f.endpoints).findActiveForMerchantAndEvent(MERCHANT_ID, ParcelStatus.CREATED);
        verify(f.endpoints).findActiveForMerchantAndEvent(MERCHANT_ID, ParcelStatus.PICKED_UP);
    }

    @Test
    @DisplayName("Customer parcels are acknowledged without any state or record")
    void customerParcelIgnored() {
        ParcelStatusChanged customer = ParcelStatusChanged.of(UUID.randomUUID(), NOW,
                new ParcelStatusChangedData(PARCEL_ID, "SD7K3M9Q2XWB", null, ParcelStatus.CREATED, 1,
                        ParcelSource.CUSTOMER, UUID.randomUUID(), null, null, null));

        assertThat(process(customer)).isEqualTo(Outcome.IGNORED);

        assertThat(f.state).isNull();
        assertThat(f.records).isEmpty();
        verify(f.states, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    @DisplayName("A redelivered processed event (crash after commit, before ack) creates no second record")
    void duplicateOfProcessed() {
        ParcelStatusChanged created = event(1);
        process(created);

        assertThat(process(created)).isEqualTo(Outcome.DUPLICATE);

        assertThat(f.records).hasSize(2);
        assertThat(f.state.getLastAppliedSequence()).isEqualTo(1);
        assertThat(f.counter("swiftdrop.merchant.events.duplicates")).isEqualTo(1);
    }

    @Test
    @DisplayName("A redelivered pending event is a duplicate, not stored twice")
    void duplicateOfPending() {
        process(event(1));
        ParcelStatusChanged third = event(3);
        assertThat(process(third)).isEqualTo(Outcome.PENDING);

        assertThat(process(third)).isEqualTo(Outcome.DUPLICATE);

        assertThat(f.pending).hasSize(1);
        assertThat(f.anomalies).isEmpty();
    }

    @Test
    @DisplayName("A gap stores the event as pending; the missing event then applies both in order")
    void gapThenFill() {
        process(event(1));
        ParcelStatusChanged third = event(3);

        assertThat(process(third)).isEqualTo(Outcome.PENDING);
        assertThat(f.state.getLastAppliedSequence()).isEqualTo(1);
        assertThat(f.pending.get(third.eventId()).getNextReconcileAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(f.records).hasSize(2);

        assertThat(process(event(2))).isEqualTo(Outcome.APPLIED);

        assertThat(f.state.getLastAppliedSequence()).isEqualTo(3);
        assertThat(f.pending).isEmpty();
        assertThat(f.records).extracting(r -> r[2]).containsExactly("CREATED", "CREATED", "PICKED_UP", "PICKED_UP",
                "AT_HUB", "AT_HUB");
    }

    @Test
    @DisplayName("The same event ID for another sequence is an identity conflict and is quarantined")
    void identityConflictWithProcessed() {
        UUID id = UUID.randomUUID();
        process(event(id, 1));

        assertThat(process(event(id, 2))).isEqualTo(Outcome.ANOMALY);

        assertThat(f.anomalies).singleElement().satisfies(a -> {
            assertThat(a.getKind()).isEqualTo(Kind.IDENTITY_CONFLICT);
            assertThat(a.getEventId()).isEqualTo(id);
            assertThat(a.getSequence()).isEqualTo(2);
        });
        assertThat(f.state.getLastAppliedSequence()).isEqualTo(1);
    }

    @Test
    @DisplayName("The same event ID as a pending event but another sequence is an identity conflict")
    void identityConflictWithPending() {
        process(event(1));
        UUID id = UUID.randomUUID();
        process(event(id, 4));

        assertThat(process(event(id, 3))).isEqualTo(Outcome.ANOMALY);

        assertThat(f.anomalies).extracting(a -> a.getKind()).containsExactly(Kind.IDENTITY_CONFLICT);
        assertThat(f.pending).hasSize(1);
    }

    @Test
    @DisplayName("Another event ID for an already processed sequence is a sequence conflict")
    void sequenceConflictWithProcessed() {
        process(event(1));

        assertThat(process(event(1))).isEqualTo(Outcome.ANOMALY);

        assertThat(f.anomalies).extracting(a -> a.getKind()).containsExactly(Kind.SEQUENCE_CONFLICT);
        assertThat(f.records).hasSize(2);
    }

    @Test
    @DisplayName("Another event ID for a pending sequence is a sequence conflict; the first one stays pending")
    void sequenceConflictWithPending() {
        process(event(1));
        ParcelStatusChanged first = event(3);
        process(first);

        assertThat(process(event(3))).isEqualTo(Outcome.ANOMALY);

        assertThat(f.anomalies).extracting(a -> a.getKind()).containsExactly(Kind.SEQUENCE_CONFLICT);
        assertThat(f.pending).containsOnlyKeys(first.eventId());
    }

    @Test
    @DisplayName("An unknown event ID for a sequence whose ID was deleted by retention is quarantined")
    void unknownDuplicate() {
        process(event(1));
        process(event(2));
        f.processed.clear();

        assertThat(process(event(1))).isEqualTo(Outcome.ANOMALY);

        assertThat(f.anomalies).extracting(a -> a.getKind()).containsExactly(Kind.UNKNOWN_DUPLICATE);
    }

    @Test
    @DisplayName("A redelivered anomalous event adds no second quarantine row")
    void anomalyRecordedOnce() {
        process(event(1));
        ParcelStatusChanged conflicting = event(1);
        process(conflicting);

        assertThat(process(conflicting)).isEqualTo(Outcome.DUPLICATE);

        assertThat(f.anomalies).hasSize(1);
    }

    @Test
    @DisplayName("An event naming a merchant that does not own the parcel creates no state")
    void ownershipMismatchOnFirstEvent() {
        when(f.parcels.findByMerchantReference(MERCHANT_ID, REFERENCE)).thenReturn(Optional.empty());

        assertThat(process(event(1))).isEqualTo(Outcome.ANOMALY);

        assertThat(f.state).isNull();
        assertThat(f.anomalies).extracting(a -> a.getKind()).containsExactly(Kind.OWNERSHIP_MISMATCH);
    }

    @Test
    @DisplayName("An event for a known parcel but another merchant never changes the recorded merchant")
    void ownershipMismatchOnKnownParcel() {
        process(event(1));
        UUID otherMerchant = UUID.randomUUID();
        ParcelStatusChanged foreign = ParcelStatusChanged.of(UUID.randomUUID(), NOW,
                new ParcelStatusChangedData(PARCEL_ID, "SD7K3M9Q2XWB", ParcelStatus.CREATED, ParcelStatus.PICKED_UP, 2,
                        ParcelSource.MERCHANT, null, otherMerchant, REFERENCE, null));

        assertThat(process(foreign)).isEqualTo(Outcome.ANOMALY);

        assertThat(f.state.getMerchantId()).isEqualTo(MERCHANT_ID);
        assertThat(f.state.getLastAppliedSequence()).isEqualTo(1);
        assertThat(f.anomalies).extracting(a -> a.getKind()).containsExactly(Kind.OWNERSHIP_MISMATCH);
    }

    @Test
    @DisplayName("Suspended when the event is processed: the sequence advances but no webhook record is created")
    void suspendedAtProcessingTime() {
        process(event(1));
        f.merchant.suspend(UUID.randomUUID(), "Review");

        assertThat(process(event(2))).isEqualTo(Outcome.APPLIED);
        assertThat(process(event(3))).isEqualTo(Outcome.APPLIED);

        assertThat(f.state.getLastAppliedSequence()).isEqualTo(3);
        assertThat(f.records).extracting(r -> r[2]).containsOnly("CREATED");
    }

    @Test
    @DisplayName("Without subscribed endpoints the event still advances the sequence")
    void noSubscribers() {
        MerchantEventFixture empty = new MerchantEventFixture();

        assertThat(empty.processor().process(event(1), "{}")).isEqualTo(Outcome.APPLIED);

        assertThat(empty.state).extracting(MerchantEventState::getLastAppliedSequence).isEqualTo(1);
        assertThat(empty.records).isEmpty();
    }
}
