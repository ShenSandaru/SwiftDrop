package com.swiftdrop.tracking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.delivery.api.PublicStatusEvent;
import com.swiftdrop.delivery.api.PublicTrackingView;

/** Stream registry and pushes, with emitters that record what would be written. */
class LiveTrackingTest {

    private static final String NUMBER = "SD7K3M9Q2XWB";
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    private ParcelQueries queries;
    private List<RecordingEmitter> created;
    private LiveTracking tracking;

    @BeforeEach
    void setUp() {
        queries = mock(ParcelQueries.class);
        created = new ArrayList<>();
        tracking = new LiveTracking(queries, () -> {
            RecordingEmitter emitter = new RecordingEmitter();
            created.add(emitter);
            return emitter;
        }, null);
        givenStatus(NUMBER, ParcelStatus.IN_TRANSIT);
    }

    @Test
    void snapshotIsSentFirstThenCommittedChangesOfThatParcel() {
        tracking.subscribe(NUMBER.toLowerCase());
        tracking.onStatusChanged(event(NUMBER, ParcelStatus.OUT_FOR_DELIVERY, 4, null));
        givenStatus("SD0000000001", ParcelStatus.CREATED);
        tracking.onStatusChanged(event("SD0000000001", ParcelStatus.PICKED_UP, 2, null));

        RecordingEmitter stream = created.getFirst();
        assertThat(stream.events).hasSize(2);
        assertThat(stream.events.get(0)).contains("event:snapshot").contains("\"status\":\"IN_TRANSIT\"");
        assertThat(stream.events.get(1)).contains("event:status").contains("id:4")
                .contains("\"status\":\"OUT_FOR_DELIVERY\"").contains("\"sequence\":4");
        assertThat(stream.completed).isFalse();
    }

    @Test
    void statusEventsCarryOnlyStatusTimeAndSequence() {
        tracking.subscribe(NUMBER);
        UUID owner = UUID.randomUUID();
        tracking.onStatusChanged(new ParcelStatusChanged(UUID.randomUUID(), "parcel.status-changed", 1, NOW,
                "delivery", new ParcelStatusChangedData(UUID.randomUUID(), NUMBER, ParcelStatus.OUT_FOR_DELIVERY,
                        ParcelStatus.FAILED, 5, ParcelSource.CUSTOMER, owner, null, null, FailureReason.REFUSED)));

        String pushed = created.getFirst().events.get(1);
        assertThat(pushed).doesNotContain(owner.toString(), "REFUSED", "CUSTOMER", "parcelId", "OUT_FOR_DELIVERY");
    }

    @Test
    void finalStatusClosesTheStreamAndFreesItsSlot() {
        tracking.subscribe(NUMBER);
        tracking.onStatusChanged(event(NUMBER, ParcelStatus.FAILED, 5, FailureReason.DAMAGED));

        assertThat(created.getFirst().completed).isTrue();
        assertThat(tracking.openStreams()).isZero();
    }

    @Test
    void finishedParcelGetsItsSnapshotAndIsClosedAtOnce() {
        givenStatus(NUMBER, ParcelStatus.DELIVERED);

        tracking.subscribe(NUMBER);

        assertThat(created.getFirst().events).singleElement().asString().contains("event:snapshot");
        assertThat(created.getFirst().completed).isTrue();
        assertThat(tracking.openStreams()).isZero();
    }

    @Test
    void unknownNumberOpensNoStream() {
        when(queries.findPublicTrackingView("SD0000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tracking.subscribe("SD0000000000")).isInstanceOf(UnknownTrackingNumberException.class);
        assertThat(created).isEmpty();
        assertThat(tracking.openStreams()).isZero();
    }

    @Test
    void streamsPerParcelAreLimited() {
        for (int i = 0; i < LiveTracking.MAX_STREAMS_PER_PARCEL; i++) {
            tracking.subscribe(NUMBER);
        }

        assertThatThrownBy(() -> tracking.subscribe(NUMBER)).isInstanceOf(TooManyStreamsException.class);
        assertThat(tracking.openStreams()).isEqualTo(LiveTracking.MAX_STREAMS_PER_PARCEL);
        // Other parcels still get streams
        givenStatus("SD0000000001", ParcelStatus.CREATED);
        tracking.subscribe("SD0000000001");
    }

    @Test
    void totalStreamsAreLimited() {
        when(queries.findPublicTrackingView(any())).thenAnswer(call -> Optional.of(view(call.getArgument(0),
                ParcelStatus.CREATED)));
        for (int i = 0; i < LiveTracking.MAX_STREAMS; i++) {
            tracking.subscribe(String.format("SD%010d", i));
        }

        assertThatThrownBy(() -> tracking.subscribe("SDZZZZZZZZZZ")).isInstanceOf(TooManyStreamsException.class);
        assertThat(tracking.openStreams()).isEqualTo(LiveTracking.MAX_STREAMS);
    }

    @Test
    void disconnectedClientsAreDroppedOnTheNextSend() {
        tracking.subscribe(NUMBER);
        tracking.subscribe(NUMBER);
        created.getFirst().broken = true;

        tracking.onStatusChanged(event(NUMBER, ParcelStatus.OUT_FOR_DELIVERY, 4, null));

        assertThat(tracking.openStreams()).isEqualTo(1);
        assertThat(created.get(1).events).hasSize(2);
    }

    @Test
    void heartbeatsGoToEveryOpenStream() {
        tracking.subscribe(NUMBER);
        givenStatus("SD0000000001", ParcelStatus.CREATED);
        tracking.subscribe("SD0000000001");

        tracking.sendHeartbeats();

        assertThat(created).allSatisfy(stream -> assertThat(stream.events.getLast()).startsWith(":heartbeat"));
    }

    private void givenStatus(String number, ParcelStatus status) {
        Optional<PublicTrackingView> found = Optional.of(view(number, status));
        when(queries.findPublicTrackingView(number)).thenReturn(found);
        when(queries.findPublicTrackingView(number.toLowerCase())).thenReturn(found);
    }

    private static PublicTrackingView view(String number, ParcelStatus status) {
        return new PublicTrackingView(number, status, "Colombo", "Kandy",
                List.of(new PublicStatusEvent(ParcelStatus.CREATED, NOW)));
    }

    private static ParcelStatusChanged event(String number, ParcelStatus status, int sequence, FailureReason reason) {
        return ParcelStatusChanged.of(UUID.randomUUID(), NOW, new ParcelStatusChangedData(UUID.randomUUID(), number,
                ParcelStatus.IN_TRANSIT, status, sequence, ParcelSource.CUSTOMER, UUID.randomUUID(), null, null, reason));
    }

    /** Renders each event as the text a client would receive, without a servlet response. */
    private static final class RecordingEmitter extends SseEmitter {

        private static final JacksonJsonHttpMessageConverter JSON = new JacksonJsonHttpMessageConverter();

        final List<String> events = new ArrayList<>();
        boolean completed;
        boolean broken;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (broken) {
                throw new IOException("client gone");
            }
            StringBuilder text = new StringBuilder();
            for (ResponseBodyEmitter.DataWithMediaType part : builder.build()) {
                Object data = part.getData();
                text.append(data instanceof String string ? string : toJson(data));
            }
            events.add(text.toString());
        }

        @Override
        public void complete() {
            completed = true;
        }

        private static String toJson(Object data) throws IOException {
            MockHttpOutputMessage output = new MockHttpOutputMessage();
            JSON.write(data, MediaType.APPLICATION_JSON, output);
            return output.getBodyAsString();
        }
    }
}
