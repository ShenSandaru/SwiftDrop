package com.swiftdrop.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import com.swiftdrop.delivery.api.ParcelStatusChanged;

class NotificationAmqpConfigurationTest {

    private final NotificationAmqpConfiguration config = new NotificationAmqpConfiguration();

    @Test
    @DisplayName("Notification RabbitMQ topology defines expected durable exchanges, queues, and bindings")
    void topologyConfiguration() {
        TopicExchange mainExchange = config.notificationParcelEventsExchange();
        assertThat(mainExchange.getName()).isEqualTo(ParcelStatusChanged.EXCHANGE);
        assertThat(mainExchange.isDurable()).isTrue();

        DirectExchange dlx = config.notificationDeadLetterExchange();
        assertThat(dlx.getName()).isEqualTo("notification.dlx");
        assertThat(dlx.isDurable()).isTrue();

        Queue dlq = config.notificationDeadLetterQueue();
        assertThat(dlq.getName()).isEqualTo("notification.parcel-status-changed.dlq");
        assertThat(dlq.isDurable()).isTrue();

        Binding dlqBinding = config.notificationDeadLetterBinding(dlq, dlx);
        assertThat(dlqBinding.getDestination()).isEqualTo("notification.parcel-status-changed.dlq");
        assertThat(dlqBinding.getExchange()).isEqualTo("notification.dlx");
        assertThat(dlqBinding.getRoutingKey()).isEqualTo("notification.parcel-status-changed.dlq");

        Queue mainQueue = config.notificationParcelStatusQueue();
        assertThat(mainQueue.getName()).isEqualTo("notification.parcel-status-changed");
        assertThat(mainQueue.isDurable()).isTrue();
        assertThat(mainQueue.getArguments()).containsEntry("x-dead-letter-exchange", "notification.dlx");
        assertThat(mainQueue.getArguments()).containsEntry("x-dead-letter-routing-key", "notification.parcel-status-changed.dlq");

        Binding mainBinding = config.notificationParcelStatusBinding(mainQueue, mainExchange);
        assertThat(mainBinding.getDestination()).isEqualTo("notification.parcel-status-changed");
        assertThat(mainBinding.getExchange()).isEqualTo(ParcelStatusChanged.EXCHANGE);
        assertThat(mainBinding.getRoutingKey()).isEqualTo(ParcelStatusChanged.EVENT_TYPE);
    }
}

