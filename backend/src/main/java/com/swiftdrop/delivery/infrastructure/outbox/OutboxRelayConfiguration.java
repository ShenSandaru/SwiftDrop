package com.swiftdrop.delivery.infrastructure.outbox;

import java.time.Clock;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.MeterRegistry;

import com.swiftdrop.delivery.domain.OutboxEventRepository;

/**
 * Wires and schedules the outbox relay, its monitor and the daily cleanup (ADR 0004 §3, §4.2, §9).
 * On by default; {@code swiftdrop.outbox.relay.enabled=false} switches all three off, which the
 * database-free tests do. This is the first use of scheduling in the backend, so it enables it.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "swiftdrop.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@EnableConfigurationProperties(OutboxRelayProperties.class)
class OutboxRelayConfiguration {

    /** Daily cleanup time (server time zone), outside busy hours. */
    static final String RETENTION_CRON = "0 30 3 * * *";

    @Bean
    OutboxRelay outboxRelay(OutboxEventRepository outbox, RabbitTemplate rabbit,
            PlatformTransactionManager transactionManager, OutboxRelayProperties properties) {
        return new OutboxRelay(outbox, new OutboxPublisher(rabbit, properties.confirmTimeout()),
                new TransactionTemplate(transactionManager), properties, Clock.systemUTC());
    }

    @Bean
    OutboxMonitor outboxMonitor(OutboxEventRepository outbox, OutboxRelay relay, OutboxRelayProperties properties,
            MeterRegistry meters) {
        return new OutboxMonitor(outbox, relay, properties, meters, Clock.systemUTC());
    }

    @Bean
    OutboxRetention outboxRetention(OutboxEventRepository outbox, PlatformTransactionManager transactionManager,
            OutboxRelayProperties properties) {
        return new OutboxRetention(outbox, new TransactionTemplate(transactionManager), properties, Clock.systemUTC());
    }

    /** Fixed delays, so a slow poll is never overlapped by the next one. */
    @Bean
    SchedulingConfigurer outboxSchedule(OutboxRelay relay, OutboxMonitor monitor, OutboxRetention retention,
            OutboxRelayProperties properties) {
        return (ScheduledTaskRegistrar tasks) -> {
            tasks.addFixedDelayTask(relay::poll, properties.pollInterval());
            tasks.addFixedDelayTask(monitor::check, properties.monitorInterval());
            tasks.addCronTask(retention::deletePublished, RETENTION_CRON);
        };
    }
}
