package com.swiftdrop.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.CannotAcquireLockException;

import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;

class NotificationEventListenerTest {

    @Test
    @DisplayName("Valid message is parsed and passed to processor with original raw payload")
    void validMessageProcessedSuccessfully() {
        String rawJson = "{\"eventId\":\"00000000-0000-0000-0000-000000000001\",\"data\":{\"sequence\":1}}";
        Message message = new Message(rawJson.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        ParcelStatusChangedData data = new ParcelStatusChangedData(
                UUID.randomUUID(),
                "SWD-1234-5678",
                ParcelStatus.IN_TRANSIT,
                ParcelStatus.DELIVERED,
                1,
                ParcelSource.CUSTOMER,
                UUID.randomUUID(),
                null,
                null,
                null,
                "HUB-01",
                1
        );

        ParcelStatusChanged event = ParcelStatusChanged.of(
                UUID.randomUUID(),
                Instant.now(),
                data
        );

        AtomicReference<String> parsedInput = new AtomicReference<>();
        AtomicReference<ParcelStatusChanged> processedEvent = new AtomicReference<>();
        AtomicReference<String> processedRawPayload = new AtomicReference<>();

        ParcelStatusEventParser stubParser = new ParcelStatusEventParser() {
            @Override
            public ParcelStatusChanged parse(String json) {
                parsedInput.set(json);
                return event;
            }
        };

        NotificationEventProcessor stubProcessor = new NotificationEventProcessor(
                null, null, null, null, null, null, null) {
            @Override
            public void process(ParcelStatusChanged ev, String rawPayload) {
                processedEvent.set(ev);
                processedRawPayload.set(rawPayload);
            }
        };

        NotificationEventListener listener = new NotificationEventListener(stubParser, stubProcessor);
        listener.onParcelStatusChanged(message);

        assertThat(parsedInput.get()).isEqualTo(rawJson);
        assertThat(processedEvent.get()).isSameAs(event);
        assertThat(processedRawPayload.get()).isEqualTo(rawJson);
    }

    @Test
    @DisplayName("Raw payload with special characters is passed unchanged to processor")
    void rawPayloadPassedUnchanged() {
        String rawJson = "{\"note\":\"Special unicode äöü & symbols <>\uD83D\uDCE6\"}";
        Message message = new Message(rawJson.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        AtomicReference<String> processedRawPayload = new AtomicReference<>();

        ParcelStatusEventParser stubParser = new ParcelStatusEventParser() {
            @Override
            public ParcelStatusChanged parse(String json) {
                return null;
            }
        };

        NotificationEventProcessor stubProcessor = new NotificationEventProcessor(
                null, null, null, null, null, null, null) {
            @Override
            public void process(ParcelStatusChanged ev, String rawPayload) {
                processedRawPayload.set(rawPayload);
            }
        };

        NotificationEventListener listener = new NotificationEventListener(stubParser, stubProcessor);
        listener.onParcelStatusChanged(message);

        assertThat(processedRawPayload.get()).isEqualTo(rawJson);
    }

    @Test
    @DisplayName("Parsing exception from parser propagates without catching")
    void parsingExceptionPropagates() {
        String rawJson = "{\"invalid\":\"json\"}";
        Message message = new Message(rawJson.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        AtomicBoolean processorInvoked = new AtomicBoolean(false);

        ParcelStatusEventParser stubParser = new ParcelStatusEventParser() {
            @Override
            public ParcelStatusChanged parse(String json) {
                throw new EventParsingException("Malformed envelope");
            }
        };

        NotificationEventProcessor stubProcessor = new NotificationEventProcessor(
                null, null, null, null, null, null, null) {
            @Override
            public void process(ParcelStatusChanged ev, String rawPayload) {
                processorInvoked.set(true);
            }
        };

        NotificationEventListener listener = new NotificationEventListener(stubParser, stubProcessor);

        assertThatThrownBy(() -> listener.onParcelStatusChanged(message))
                .isInstanceOf(EventParsingException.class)
                .hasMessage("Malformed envelope");

        assertThat(processorInvoked.get()).isFalse();
    }

    @Test
    @DisplayName("Processor exception propagates without catching to trigger retry interceptor")
    void processorExceptionPropagates() {
        String rawJson = "{\"valid\":\"json\"}";
        Message message = new Message(rawJson.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        ParcelStatusEventParser stubParser = new ParcelStatusEventParser() {
            @Override
            public ParcelStatusChanged parse(String json) {
                return null;
            }
        };

        NotificationEventProcessor stubProcessor = new NotificationEventProcessor(
                null, null, null, null, null, null, null) {
            @Override
            public void process(ParcelStatusChanged ev, String rawPayload) {
                throw new CannotAcquireLockException("Lock contention on parcel");
            }
        };

        NotificationEventListener listener = new NotificationEventListener(stubParser, stubProcessor);

        assertThatThrownBy(() -> listener.onParcelStatusChanged(message))
                .isInstanceOf(CannotAcquireLockException.class)
                .hasMessage("Lock contention on parcel");
    }

    @Test
    @DisplayName("Null message throws NullPointerException immediately")
    void nullMessageThrows() {
        NotificationEventListener listener = new NotificationEventListener(
                new ParcelStatusEventParser(),
                new NotificationEventProcessor(null, null, null, null, null, null, null)
        );

        assertThatThrownBy(() -> listener.onParcelStatusChanged(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("message must not be null");
    }
}
