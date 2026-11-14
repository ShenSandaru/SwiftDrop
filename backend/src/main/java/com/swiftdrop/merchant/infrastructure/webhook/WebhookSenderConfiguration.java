package com.swiftdrop.merchant.infrastructure.webhook;

import java.time.Clock;
import java.util.random.RandomGenerator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.MeterRegistry;

import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookAttemptRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.merchant.security.SecretCipher;

/**
 * The scheduled webhook sender (SD-411). On by default; {@code swiftdrop.merchant.webhooks.sender.enabled=false}
 * switches it off (tests without a database, or to let delivery records wait).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "swiftdrop.merchant.webhooks.sender.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WebhookSenderProperties.class)
@EnableScheduling
class WebhookSenderConfiguration {

    @Bean
    WebhookSender webhookSender(MerchantWebhookDeliveryRepository deliveries, MerchantWebhookAttemptRepository attempts,
            MerchantWebhookEndpointRepository endpoints, MerchantRepository merchants, SecretCipher secrets,
            WebhookHttpClient http, WebhookSenderProperties properties, PlatformTransactionManager transactionManager,
            MeterRegistry meters) {
        return new WebhookSender(deliveries, attempts, endpoints, merchants, secrets, http, properties,
                new TransactionTemplate(transactionManager), meters, Clock.systemUTC(), RandomGenerator.getDefault());
    }

    @Bean
    SchedulingConfigurer webhookSenderSchedule(WebhookSender sender, WebhookSenderProperties properties) {
        return registrar -> registrar.addFixedDelayTask(sender::sendDue, properties.pollInterval());
    }
}
