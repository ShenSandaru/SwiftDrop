package com.swiftdrop.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.merchant.api.MerchantLookup;
import com.swiftdrop.notification.application.NotificationEventListener;
import com.swiftdrop.notification.application.NotificationEventProcessor;
import com.swiftdrop.notification.application.EmailNotificationSender;
import com.swiftdrop.notification.application.NotificationReconciliationService;
import com.swiftdrop.notification.application.NotificationTemplateResolver;
import com.swiftdrop.notification.application.ParcelStatusEventParser;
import com.swiftdrop.notification.application.SmsNotificationSender;
import com.swiftdrop.notification.domain.NotificationConsumerStateRepository;
import com.swiftdrop.notification.domain.NotificationPendingEventRepository;
import com.swiftdrop.notification.domain.NotificationRecordRepository;

class NotificationConsumerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    NotificationAmqpConfiguration.class,
                    NotificationReconciliationConfiguration.class,
                    NotificationEventListener.class,
                    NotificationEventProcessor.class);

    private ApplicationContextRunner withMockedDependencies() {
        return contextRunner
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(NotificationConsumerStateRepository.class, () -> mock(NotificationConsumerStateRepository.class))
                .withBean(NotificationPendingEventRepository.class, () -> mock(NotificationPendingEventRepository.class))
                .withBean(NotificationRecordRepository.class, () -> mock(NotificationRecordRepository.class))
                .withBean(NotificationTemplateResolver.class, () -> mock(NotificationTemplateResolver.class))
                .withBean(ParcelStatusEventParser.class, () -> mock(ParcelStatusEventParser.class))
                .withBean(ParcelEvents.class, () -> mock(ParcelEvents.class))
                .withBean(UserDirectory.class, () -> mock(UserDirectory.class))
                .withBean(MerchantLookup.class, () -> mock(MerchantLookup.class))
                .withBean(EmailNotificationSender.class, () -> mock(EmailNotificationSender.class))
                .withBean(SmsNotificationSender.class, () -> mock(SmsNotificationSender.class));
    }

    @Test
    void consumerAndReconciliationEnabledWhenBothPropertiesMissing() {
        withMockedDependencies().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(NotificationAmqpConfiguration.class);
            assertThat(context).hasSingleBean(NotificationEventListener.class);
            assertThat(context).hasSingleBean(NotificationEventProcessor.class);
            assertThat(context).hasSingleBean(NotificationReconciliationConfiguration.class);
            assertThat(context).hasSingleBean(NotificationReconciliationService.class);
        });
    }

    @Test
    void consumerAndReconciliationEnabledWhenConsumerTrueAndReconciliationMissing() {
        withMockedDependencies()
                .withPropertyValues("swiftdrop.notification.consumer.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(NotificationEventListener.class);
                    assertThat(context).hasSingleBean(NotificationEventProcessor.class);
                    assertThat(context).hasSingleBean(NotificationReconciliationService.class);
                });
    }

    @Test
    void bothDisabledWhenConsumerFalseAndReconciliationMissing() {
        contextRunner
                .withPropertyValues("swiftdrop.notification.consumer.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(NotificationAmqpConfiguration.class);
                    assertThat(context).doesNotHaveBean(NotificationEventListener.class);
                    assertThat(context).doesNotHaveBean(NotificationEventProcessor.class);
                    assertThat(context).doesNotHaveBean(NotificationReconciliationConfiguration.class);
                    assertThat(context).doesNotHaveBean(NotificationReconciliationService.class);
                });
    }

    @Test
    void bothDisabledWhenConsumerFalseEvenIfReconciliationTrue() {
        contextRunner
                .withPropertyValues(
                        "swiftdrop.notification.consumer.enabled=false",
                        "swiftdrop.notification.reconciliation.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(NotificationAmqpConfiguration.class);
                    assertThat(context).doesNotHaveBean(NotificationEventListener.class);
                    assertThat(context).doesNotHaveBean(NotificationEventProcessor.class);
                    assertThat(context).doesNotHaveBean(NotificationReconciliationConfiguration.class);
                    assertThat(context).doesNotHaveBean(NotificationReconciliationService.class);
                });
    }

    @Test
    void consumerEnabledAndReconciliationDisabledWhenConsumerTrueAndReconciliationFalse() {
        withMockedDependencies()
                .withPropertyValues(
                        "swiftdrop.notification.consumer.enabled=true",
                        "swiftdrop.notification.reconciliation.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(NotificationAmqpConfiguration.class);
                    assertThat(context).hasSingleBean(NotificationEventListener.class);
                    assertThat(context).hasSingleBean(NotificationEventProcessor.class);
                    assertThat(context).doesNotHaveBean(NotificationReconciliationConfiguration.class);
                    assertThat(context).doesNotHaveBean(NotificationReconciliationService.class);
                });
    }

    @Test
    void bothEnabledWhenConsumerTrueAndReconciliationTrue() {
        withMockedDependencies()
                .withPropertyValues(
                        "swiftdrop.notification.consumer.enabled=true",
                        "swiftdrop.notification.reconciliation.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(NotificationAmqpConfiguration.class);
                    assertThat(context).hasSingleBean(NotificationEventListener.class);
                    assertThat(context).hasSingleBean(NotificationEventProcessor.class);
                    assertThat(context).hasSingleBean(NotificationReconciliationConfiguration.class);
                    assertThat(context).hasSingleBean(NotificationReconciliationService.class);
                });
    }
}
