package com.swiftdrop.delivery.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.swiftdrop.delivery.domain.OutboxEvent;
import com.swiftdrop.delivery.infrastructure.outbox.OutboxPublisher.Result;

class OutboxPublisherTest {

    private static final Instant CREATED = Instant.parse("2026-11-09T10:00:00Z");

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final OutboxPublisher publisher = new OutboxPublisher(rabbit, Duration.ofMillis(200));
    private final OutboxEvent row = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), 3, "parcel.status-changed",
            "{\"eventId\":\"x\"}", CREATED);

    @Test
    @DisplayName("A positive confirm without a return is CONFIRMED")
    void confirmed() {
        onSend(correlation -> correlation.getFuture().complete(new CorrelationData.Confirm(true, null)));
        assertThat(publisher.publish(row)).isEqualTo(Result.CONFIRMED);
    }

    @Test
    @DisplayName("A negative confirm is NACKED")
    void nacked() {
        onSend(correlation -> correlation.getFuture().complete(new CorrelationData.Confirm(false, "reject-publish")));
        assertThat(publisher.publish(row)).isEqualTo(Result.NACKED);
    }

    @Test
    @DisplayName("A returned message is RETURNED even though the broker confirms it")
    void returned() {
        onSend(correlation -> {
            correlation.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE",
                    "swiftdrop.events", "parcel.status-changed"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
        });
        assertThat(publisher.publish(row)).isEqualTo(Result.RETURNED);
    }

    @Test
    @DisplayName("No confirm within the timeout is CONFIRM_TIMEOUT")
    void timeout() {
        onSend(correlation -> { });
        assertThat(publisher.publish(row)).isEqualTo(Result.CONFIRM_TIMEOUT);
    }

    @Test
    @DisplayName("A broker that cannot be reached throws, so the relay ends the poll")
    void brokerUnavailable() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("refused")))
                .when(rabbit).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
        assertThatThrownBy(() -> publisher.publish(row)).isInstanceOf(AmqpConnectException.class);
    }

    @Test
    @DisplayName("Messages carry the ADR 0004 §2 properties and the payload as the body")
    void messageProperties() {
        Message message = OutboxPublisher.toMessage(row);

        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(row.getEventId().toString());
        assertThat(message.getMessageProperties().getType()).isEqualTo("parcel.status-changed");
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getTimestamp()).isEqualTo(Date.from(CREATED));
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"eventId\":\"x\"}");
    }

    private void onSend(Consumer<CorrelationData> broker) {
        doAnswer(invocation -> {
            broker.accept(invocation.getArgument(3));
            return null;
        }).when(rabbit).send(eq("swiftdrop.events"), eq("parcel.status-changed"), any(Message.class),
                any(CorrelationData.class));
    }
}
