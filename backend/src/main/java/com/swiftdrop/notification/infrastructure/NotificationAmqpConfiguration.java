package com.swiftdrop.notification.infrastructure;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.swiftdrop.delivery.api.ParcelStatusChanged;

/**
 * Declares the RabbitMQ topology for consuming {@code parcel.status-changed} events
 * and routing dead letters (ADR 0004 §5, §11, docs/architecture/notification-system-architecture.md).
 */
@Configuration(proxyBeanMethods = false)
public class NotificationAmqpConfiguration {

    public static final String NOTIFICATION_QUEUE = "notification.parcel-status-changed";
    public static final String NOTIFICATION_DLX = "notification.dlx";
    public static final String NOTIFICATION_DLQ = "notification.parcel-status-changed.dlq";
    public static final String NOTIFICATION_DLQ_ROUTING_KEY = "notification.parcel-status-changed.dlq";

    /**
     * References the shared durable topic exchange {@code swiftdrop.events} declared by Delivery.
     */
    @Bean
    TopicExchange notificationParcelEventsExchange() {
        return ExchangeBuilder.topicExchange(ParcelStatusChanged.EXCHANGE).durable(true).build();
    }

    /**
     * Declares the direct dead-letter exchange {@code notification.dlx}.
     */
    @Bean
    DirectExchange notificationDeadLetterExchange() {
        return ExchangeBuilder.directExchange(NOTIFICATION_DLX).durable(true).build();
    }

    /**
     * Declares the dead-letter queue {@code notification.parcel-status-changed.dlq}.
     */
    @Bean
    Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(NOTIFICATION_DLQ).build();
    }

    /**
     * Binds the dead-letter queue to {@code notification.dlx} using its routing key.
     */
    @Bean
    Binding notificationDeadLetterBinding(Queue notificationDeadLetterQueue, DirectExchange notificationDeadLetterExchange) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
                .to(notificationDeadLetterExchange)
                .with(NOTIFICATION_DLQ_ROUTING_KEY);
    }

    /**
     * Declares the main durable consumer queue configured with DLX arguments.
     */
    @Bean
    Queue notificationParcelStatusQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .deadLetterExchange(NOTIFICATION_DLX)
                .deadLetterRoutingKey(NOTIFICATION_DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * Binds the main consumer queue to {@code swiftdrop.events} with routing key {@code parcel.status-changed}.
     */
    @Bean
    Binding notificationParcelStatusBinding(Queue notificationParcelStatusQueue, TopicExchange notificationParcelEventsExchange) {
        return BindingBuilder.bind(notificationParcelStatusQueue)
                .to(notificationParcelEventsExchange)
                .with(ParcelStatusChanged.EVENT_TYPE);
    }
}

