package com.swiftdrop.merchant.infrastructure.messaging;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.transaction.PlatformTransactionManager;

import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.merchant.application.InvalidMerchantEventException;
import com.swiftdrop.merchant.application.MerchantEventJson;
import com.swiftdrop.merchant.application.MerchantEventMetrics;
import com.swiftdrop.merchant.application.MerchantEventProcessor;
import com.swiftdrop.merchant.domain.MerchantEventStateRepository;
import com.swiftdrop.merchant.domain.MerchantPendingEventRepository;
import com.swiftdrop.merchant.domain.MerchantProcessedEventRepository;
import com.swiftdrop.merchant.application.MerchantEventProperties;
import com.swiftdrop.merchant.application.MerchantEventReconciler;
import com.swiftdrop.merchant.application.MerchantEventRetention;

/**
 * The merchant event consumer (SD-410): the queue listener with its retry policy, the gap reconciler and the
 * processed-ID retention. On by default; {@code swiftdrop.merchant.events.consumer.enabled=false} switches all
 * three off (tests without RabbitMQ, or to let events wait in the queue).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "swiftdrop.merchant.events.consumer.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
class MerchantEventConsumerConfiguration {

    static final String CONTAINER_FACTORY = "merchantEventListenerContainerFactory";

    @Bean(name = CONTAINER_FACTORY)
    SimpleRabbitListenerContainerFactory merchantEventListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
            MerchantEventProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        // A failed message is never requeued; after the retries it is rejected and dead-lettered.
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryAdvice(properties));
        return factory;
    }

    /**
     * {@code attempts} in total (first try included), exponential backoff; invalid messages are not retried.
     */
    static MethodInterceptor retryAdvice(MerchantEventProperties properties) {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(properties.attempts() - 1)
                .delay(properties.retryInitialDelay())
                .multiplier(2)
                .maxDelay(properties.retryMaxDelay())
                .predicate(MerchantEventConsumerConfiguration::isRetryable)
                .build();
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(policy)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    /** False if the failure, or any of its causes, means the message itself is unusable. */
    static boolean isRetryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof AmqpRejectAndDontRequeueException || cause instanceof MessageConversionException
                    || cause instanceof InvalidMerchantEventException) {
                return false;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return true;
    }

    @Bean
    MerchantEventListener merchantEventListener(MerchantEventJson json, MerchantEventProcessor processor) {
        return new MerchantEventListener(json, processor);
    }

    @Bean
    MerchantEventReconciler merchantEventReconciler(MerchantEventStateRepository states,
            MerchantPendingEventRepository pending, MerchantProcessedEventRepository processed, ParcelEvents history,
            MerchantEventProcessor processor, MerchantEventJson json, MerchantEventMetrics metrics,
            MerchantEventProperties properties, PlatformTransactionManager transactionManager) {
        return new MerchantEventReconciler(states, pending, processed, history, processor, json, metrics, properties,
                transactionManager);
    }

    @Bean
    MerchantEventRetention merchantEventRetention(MerchantProcessedEventRepository processed,
            MerchantEventProperties properties, PlatformTransactionManager transactionManager) {
        return new MerchantEventRetention(processed, properties, transactionManager);
    }

    @Bean
    SchedulingConfigurer merchantEventSchedules(MerchantEventReconciler reconciler, MerchantEventRetention retention,
            MerchantEventProperties properties) {
        return registrar -> {
            registrar.addFixedDelayTask(reconciler::reconcileDue, properties.reconcileInterval());
            registrar.addCronTask(retention::deleteExpired, "0 45 3 * * *");
        };
    }
}
