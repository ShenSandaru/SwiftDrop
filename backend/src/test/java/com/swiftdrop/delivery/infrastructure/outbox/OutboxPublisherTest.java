package com.swiftdrop.delivery.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.domain.OutboxEvent;

/** One publish and its broker answer (ADR 0004 §3, §11 "outbox crash windows"). */
class OutboxPublisherTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-11-09T10:15:30Z");

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final OutboxPublisher publisher = new OutboxPublisher(rabbit, Duration.ofMillis(100));
    private final OutboxEvent row = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), 2,
            ParcelStatusChanged.EVENT_TYPE, "{\"eventType\":\"parcel.status-changed\"}", OCCURRED_AT);

    /** Lets the mocked broker answer the publish through its correlation data. */
    private void brokerAnswers(Consumer<CorrelationData> answer) {
        doAnswer(invocation -> {
            answer.accept(invocation.getArgument(3));
            return null;
        }).when(rabbit).send(eq(ParcelStatusChanged.EXCHANGE), eq(ParcelStatusChanged.EVENT_TYPE), any(Message.class),
                any(CorrelationData.class));
    }

    @Test
    void positiveConfirmIsConfirmed() {
        brokerAnswers(correlation -> correlation.getFuture().complete(new CorrelationData.Confirm(true, null)));

        assertThat(publisher.publish(row)).isEqualTo(PublishOutcome.CONFIRMED);
    }

    @Test
    void negativeConfirmIsNacked() {
        brokerAnswers(correlation -> correlation.getFuture().complete(new CorrelationData.Confirm(false, "refused")));

        assertThat(publisher.publish(row)).isEqualTo(PublishOutcome.NACKED);
    }

    @Test
    void returnBeforeAPositiveConfirmIsReturned() {
        brokerAnswers(correlation -> {
            correlation.setReturned(new ReturnedMessage(OutboxPublisher.toMessage(row), 312, "NO_ROUTE",
                    ParcelStatusChanged.EXCHANGE, ParcelStatusChanged.EVENT_TYPE));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
        });

        assertThat(publisher.publish(row)).isEqualTo(PublishOutcome.RETURNED);
    }

    @Test
    void missingConfirmTimesOut() {
        brokerAnswers(correlation -> {
            // no answer
        });

        assertThat(publisher.publish(row)).isEqualTo(PublishOutcome.CONFIRM_TIMEOUT);
    }

    @Test
    void connectionFailureMeansBrokerUnavailable() {
        doThrow(new AmqpConnectException(new IOException("connection refused"))).when(rabbit)
                .send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        assertThat(publisher.publish(row)).isEqualTo(PublishOutcome.BROKER_UNAVAILABLE);
    }

    @Test
    void failedConfirmFutureMeansBrokerUnavailable() {
        brokerAnswers(correlation -> correlation.getFuture().completeExceptionally(new IOException("channel closed")));

        assertThat(publisher.publish(row)).isEqualTo(PublishOutcome.BROKER_UNAVAILABLE);
    }

    @Test
    void messageCarriesTheAdrPropertiesAndTheStoredPayload() {
        brokerAnswers(correlation -> correlation.getFuture().complete(new CorrelationData.Confirm(true, null)));
        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);

        publisher.publish(row);

        verify(rabbit).send(eq(ParcelStatusChanged.EXCHANGE), eq(ParcelStatusChanged.EVENT_TYPE), sent.capture(),
                correlation.capture());
        Message message = sent.getValue();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(row.getEventId().toString());
        assertThat(message.getMessageProperties().getType()).isEqualTo(ParcelStatusChanged.EVENT_TYPE);
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getTimestamp().toInstant()).isEqualTo(OCCURRED_AT);
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(row.getPayload());
        assertThat(correlation.getValue().getId()).isEqualTo(row.getEventId().toString());
    }
}
