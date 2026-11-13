package com.swiftdrop.notification.infrastructure;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;

import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.notification.application.EventParsingException;

/**
 * Declares the RabbitMQ topology for consuming {@code parcel.status-changed} events
 * and routing dead letters (ADR 0004 §5, §11, docs/architecture/notification-system-architecture.md),
 * and configures the dedicated listener container factory with method-level transaction isolation,
 * stateless retry backoff, and DLQ recovery.
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

    /**
     * Configures the dedicated container factory for Notification event listeners (Pattern A).
     * <p>
     * Guarantees:
     * <ul>
     *   <li>{@code AcknowledgeMode.AUTO} and {@code channelTransacted = false} guarantee that
     *       RabbitMQ ACKs are sent only after the processor's JPA transaction successfully commits.</li>
     *   <li>No container-level {@code transactionManager} is set, allowing each stateless retry
     *       attempt to execute in a brand-new, isolated JPA transaction demarcated on
     *       {@code NotificationEventProcessor.process(...)}.</li>
     *   <li>Stateless retry interceptor configured with {@code maxRetries(2)} provides exactly 3 total
     *       attempts (1 initial attempt + 2 retries with 1s initial, 2.0x multiplier, 5s max delay).</li>
     *   <li>Explicit retry classification excludes fatal poison pills (zero retries) and includes
     *       approved transient database exceptions. Exclusions take precedence over inclusions.</li>
     *   <li>Upon retry exhaustion or non-retryable exception, {@link RejectAndDontRequeueRecoverer}
     *       rejects the message without requeue, routing it directly to the DLQ.</li>
     * </ul>
     */
    @Bean(name = "notificationRabbitListenerContainerFactory")
    SimpleRabbitListenerContainerFactory notificationRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setChannelTransacted(false);
        factory.setDefaultRequeueRejected(false);

        // Stateless retry interceptor: exactly 3 total attempts (1 initial attempt + 2 retries)
        StatelessRetryOperationsInterceptor retryInterceptor = RetryInterceptorBuilder.stateless()
                .maxRetries(2)
                .backOffOptions(1000, 2.0, 5000)
                .configureRetryPolicy(policy -> policy
                        // Fatal exceptions: do not retry
                        .excludes(
                                EventParsingException.class,
                                AmqpRejectAndDontRequeueException.class,
                                IllegalArgumentException.class,
                                NullPointerException.class
                        )
                        // Transient database / lock exceptions: retry with backoff
                        .includes(
                                TransientDataAccessException.class,
                                CannotAcquireLockException.class,
                                PessimisticLockingFailureException.class
                        )
                )
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();

        factory.setAdviceChain(retryInterceptor);
        return factory;
    }
}
