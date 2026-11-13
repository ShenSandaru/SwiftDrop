package com.swiftdrop.delivery.infrastructure.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.domain.OutboxEvent;

/**
 * Publishes one outbox row to the {@code swiftdrop.events} exchange and waits for the broker's
 * publisher confirm (ADR 0004 §2, §3). Needs a template with correlated publisher confirms, returns
 * and the mandatory flag ({@code spring.rabbitmq.*} in {@code application.properties}).
 */
class OutboxPublisher {

    private final RabbitTemplate rabbit;
    private final Duration confirmTimeout;

    OutboxPublisher(RabbitTemplate rabbit, Duration confirmTimeout) {
        this.rabbit = rabbit;
        this.confirmTimeout = confirmTimeout;
    }

    PublishOutcome publish(OutboxEvent row) {
        CorrelationData correlation = new CorrelationData(row.getEventId().toString());
        try {
            rabbit.send(ParcelStatusChanged.EXCHANGE, row.getEventType(), toMessage(row), correlation);
        } catch (AmqpException e) {
            return PublishOutcome.BROKER_UNAVAILABLE;
        }

        CorrelationData.Confirm confirm;
        try {
            confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return PublishOutcome.CONFIRM_TIMEOUT;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PublishOutcome.CONFIRM_TIMEOUT;
        } catch (ExecutionException e) {
            return PublishOutcome.BROKER_UNAVAILABLE;
        }
        if (!confirm.ack()) {
            return PublishOutcome.NACKED;
        }
        // The broker sends a return before the confirm, so it is already recorded here
        return correlation.getReturned() != null ? PublishOutcome.RETURNED : PublishOutcome.CONFIRMED;
    }

    /** AMQP properties of ADR 0004 §2: message ID = event ID, type, JSON, occurrence time, persistent. */
    static Message toMessage(OutboxEvent row) {
        return MessageBuilder.withBody(row.getPayload().getBytes(StandardCharsets.UTF_8))
                .setMessageId(row.getEventId().toString())
                .setType(row.getEventType())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setTimestamp(Date.from(row.getCreatedAt()))
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }
}
