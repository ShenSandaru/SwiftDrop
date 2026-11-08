package com.swiftdrop.delivery.infrastructure.outbox;

import java.time.Clock;
import java.time.Duration;

import io.micrometer.core.instrument.MeterRegistry;

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

import com.swiftdrop.delivery.domain.OutboxEventRepository;

/**
 * Runs the outbox relay (SD-502) unless {@code swiftdrop.outbox.relay.enabled=false}, which tests use so
 * they never publish to a developer's local broker.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "swiftdrop.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@EnableConfigurationProperties(OutboxRelayProperties.class)
class OutboxRelayConfiguration {

    static final Duration WATCHDOG_INTERVAL = Duration.ofSeconds(30);
    static final String RETENTION_CRON = "0 30 3 * * *";

    @Bean
    OutboxRelayMetrics outboxRelayMetrics(MeterRegistry registry) {
        return new OutboxRelayMetrics(registry);
    }

    @Bean
    OutboxRelay outboxRelay(OutboxEventRepository outbox, RabbitTemplate rabbit,
            PlatformTransactionManager transactionManager, OutboxRelayMetrics metrics,
            OutboxRelayProperties properties) {
        return new OutboxRelay(outbox, new OutboxPublisher(rabbit, properties.confirmTimeout()),
                new TransactionTemplate(transactionManager), metrics, properties, Clock.systemUTC());
    }

    @Bean
    OutboxRetention outboxRetention(OutboxEventRepository outbox, OutboxRelayProperties properties) {
        return new OutboxRetention(outbox, properties.retention(), Clock.systemUTC());
    }

    @Bean
    SchedulingConfigurer outboxRelaySchedule(OutboxRelay relay, OutboxRetention retention,
            OutboxRelayProperties properties) {
        return (ScheduledTaskRegistrar registrar) -> {
            registrar.addFixedDelayTask(relay::poll, properties.pollInterval());
            registrar.addFixedDelayTask(relay::checkStalled, WATCHDOG_INTERVAL);
            registrar.addCronTask(retention::deleteExpired, RETENTION_CRON);
        };
    }
}
