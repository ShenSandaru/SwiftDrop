package com.swiftdrop.delivery.infrastructure.outbox;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.swiftdrop.delivery.api.ParcelStatusChanged;

/**
 * Declares the durable topic exchange {@code swiftdrop.events}, which {@code delivery} owns as the
 * publisher (ADR 0004 §5). Spring AMQP declares it when the first connection opens. Queues and their
 * bindings belong to the consumer modules.
 */
@Configuration(proxyBeanMethods = false)
class ParcelEventsExchangeConfiguration {

    @Bean
    TopicExchange parcelEventsExchange() {
        return ExchangeBuilder.topicExchange(ParcelStatusChanged.EXCHANGE).durable(true).build();
    }
}
