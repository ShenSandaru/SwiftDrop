package com.swiftdrop.merchant.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.swiftdrop.delivery.api.ParcelEventTopology;

/**
 * The merchant module's queue for parcel status events and its dead-letter queue (SD-501, ADR 0004 §5.1).
 * The consumer that fills webhook delivery records from it comes with SD-410; until then messages wait
 * here, bounded by the maximum length.
 *
 * <p>The values are queue arguments: changing them later means deleting and re-declaring the queue
 * (ADR 0004 §5.4).
 */
@Configuration(proxyBeanMethods = false)
class MerchantEventQueueConfiguration {

    static final String QUEUE = "merchant.parcel-status-changed";
    static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";
    /** Declared identically by every consumer module; declarations are idempotent. */
    static final String DEAD_LETTER_EXCHANGE = "swiftdrop.dead-letter";
    static final int DELIVERY_LIMIT = 5;
    static final int MAX_LENGTH = 100_000;

    @Bean
    Queue merchantParcelStatusChangedQueue() {
        return QueueBuilder.durable(QUEUE)
                .quorum()
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(QUEUE)
                .deliveryLimit(DELIVERY_LIMIT)
                .maxLength(MAX_LENGTH)
                .overflow(QueueBuilder.Overflow.rejectPublish)
                // No builder method; at-least-once needs reject-publish and the stream_queue feature flag.
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .build();
    }

    /** No dead-letter exchange of its own (no cycle) and no length limit: it must never refuse a dead letter. */
    @Bean
    Queue merchantParcelStatusChangedDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).quorum().build();
    }

    @Bean
    DirectExchange merchantDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Binding merchantParcelStatusChangedBinding() {
        return new Binding(QUEUE, Binding.DestinationType.QUEUE, ParcelEventTopology.EXCHANGE,
                ParcelEventTopology.STATUS_CHANGED_ROUTING_KEY, null);
    }

    @Bean
    Binding merchantDeadLetterBinding() {
        return BindingBuilder.bind(merchantParcelStatusChangedDeadLetterQueue())
                .to(merchantDeadLetterExchange())
                .with(QUEUE);
    }
}
