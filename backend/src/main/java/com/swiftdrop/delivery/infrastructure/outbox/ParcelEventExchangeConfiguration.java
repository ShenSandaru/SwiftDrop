package com.swiftdrop.delivery.infrastructure.outbox;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.swiftdrop.delivery.api.ParcelEventTopology;

/**
 * Declares {@code swiftdrop.events}, the durable topic exchange {@code delivery} publishes to
 * (ADR 0004 §5). Consumers declare and bind their own queues.
 */
@Configuration(proxyBeanMethods = false)
class ParcelEventExchangeConfiguration {

    @Bean
    TopicExchange parcelEventsExchange() {
        return new TopicExchange(ParcelEventTopology.EXCHANGE, true, false);
    }
}
