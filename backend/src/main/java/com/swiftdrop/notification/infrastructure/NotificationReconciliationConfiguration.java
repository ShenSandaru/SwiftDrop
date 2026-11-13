package com.swiftdrop.notification.infrastructure;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import com.swiftdrop.notification.application.NotificationReconciliationService;

/**
 * Wires and schedules the Notification pending event reconciliation task (ADR 0004 §4.3).
 * On by default; {@code swiftdrop.notification.reconciliation.enabled=false} switches it off.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "swiftdrop.notification.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@EnableConfigurationProperties(NotificationReconciliationProperties.class)
public class NotificationReconciliationConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock notificationClock() {
        return Clock.systemUTC();
    }

    @Bean
    NotificationReconciliationService notificationReconciliationService(
            com.swiftdrop.notification.domain.NotificationPendingEventRepository pendingEventRepository,
            com.swiftdrop.notification.application.NotificationEventProcessor processor,
            NotificationReconciliationProperties properties,
            Clock notificationClock) {
        return new NotificationReconciliationService(pendingEventRepository, processor, properties, notificationClock);
    }

    /**
     * Fixed delay schedule ensures slow reconciliation runs never overlap with the next one.
     */
    @Bean
    SchedulingConfigurer notificationReconciliationSchedule(
            NotificationReconciliationService reconciliationService,
            NotificationReconciliationProperties properties) {
        return (ScheduledTaskRegistrar tasks) -> {
            tasks.addFixedDelayTask(reconciliationService::reconcile, properties.interval());
        };
    }
}

