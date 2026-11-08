package com.swiftdrop.delivery.infrastructure.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.swiftdrop.delivery.api.ParcelEventTopology;
import com.swiftdrop.delivery.domain.OutboxEvent;

/**
 * Publishes one outbox row to {@code swiftdrop.events} and waits for the broker's verdict (ADR 0004 §2,
 * §3): persistent, mandatory, with a publisher confirm. Needs
 * {@code spring.rabbitmq.publisher-confirm-type=correlated}, {@code publisher-returns=true} and
 * {@code template.mandatory=true}.
 */
class OutboxPublisher {

    enum Result {
        /** Positive confirm and not returned: the broker has the message. */
        CONFIRMED,
        /** Negative confirm: the broker refused it, for example a queue full with {@code reject-publish}. */
        NACKED,
        /** Returned as unroutable: no queue is bound for the routing key. */
        RETURNED,
        /** No confirm within the timeout; the broker may or may not have the message. */
        CONFIRM_TIMEOUT
    }

    private final RabbitTemplate rabbit;
    private final Duration confirmTimeout;

    OutboxPublisher(RabbitTemplate rabbit, Duration confirmTimeout) {
        this.rabbit = rabbit;
        this.confirmTimeout = confirmTimeout;
    }

    /**
     * @throws AmqpException if the broker cannot be reached or the channel fails
     */
    Result publish(OutboxEvent row) {
        CorrelationData correlation = new CorrelationData(row.getEventId().toString());
        rabbit.send(ParcelEventTopology.EXCHANGE, row.getEventType(), toMessage(row), correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                return Result.NACKED;
            }
            // Spring AMQP sets a return before it completes the confirm future.
            return correlation.getReturned() != null ? Result.RETURNED : Result.CONFIRMED;
        } catch (TimeoutException e) {
            return Result.CONFIRM_TIMEOUT;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.CONFIRM_TIMEOUT;
        } catch (ExecutionException e) {
            throw new AmqpException("Publisher confirm failed", e.getCause());
        }
    }

    /** AMQP properties per ADR 0004 §2: message ID = event ID, type = event type, JSON, persistent. */
    static Message toMessage(OutboxEvent row) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(row.getEventId().toString());
        properties.setType(row.getEventType());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setTimestamp(Date.from(row.getCreatedAt()));
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        return new Message(row.getPayload().getBytes(StandardCharsets.UTF_8), properties);
    }
}
