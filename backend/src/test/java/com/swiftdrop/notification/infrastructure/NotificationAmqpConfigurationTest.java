package com.swiftdrop.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.aopalliance.aop.Advice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.core.retry.RetryOperations;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;

import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.notification.application.EventParsingException;

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

    @Test
    @DisplayName("notificationRabbitListenerContainerFactory is configured with Pattern A transaction and retry settings")
    void containerFactoryConfiguration() throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory() {
            @Override
            public org.springframework.amqp.rabbit.connection.Connection createConnection() {
                return null;
            }

            @Override
            public String getHost() {
                return "localhost";
            }

            @Override
            public int getPort() {
                return 5672;
            }

            @Override
            public String getVirtualHost() {
                return "/";
            }

            @Override
            public String getUsername() {
                return "guest";
            }

            @Override
            public void addConnectionListener(ConnectionListener listener) {}

            @Override
            public boolean removeConnectionListener(ConnectionListener listener) {
                return false;
            }

            @Override
            public void clearConnectionListeners() {}
        };

        SimpleRabbitListenerContainerFactory factory = config.notificationRabbitListenerContainerFactory(connectionFactory);

        // AUTO acknowledge mode
        Field ackModeField = findField(SimpleRabbitListenerContainerFactory.class, "acknowledgeMode");
        ackModeField.setAccessible(true);
        assertThat(ackModeField.get(factory)).isEqualTo(AcknowledgeMode.AUTO);

        // Channel transacted disabled (Pattern A with publisher confirms)
        Field transactedField = findField(SimpleRabbitListenerContainerFactory.class, "channelTransacted");
        transactedField.setAccessible(true);
        assertThat(transactedField.get(factory)).isEqualTo(Boolean.FALSE);

        // defaultRequeueRejected is false (poison pills/rejected messages route to DLQ)
        Field requeueField = findField(SimpleRabbitListenerContainerFactory.class, "defaultRequeueRejected");
        requeueField.setAccessible(true);
        assertThat(requeueField.get(factory)).isEqualTo(Boolean.FALSE);

        // No container-level PlatformTransactionManager (method-level @Transactional Pattern A)
        Field txManagerField = findField(SimpleRabbitListenerContainerFactory.class, "transactionManager");
        txManagerField.setAccessible(true);
        assertThat(txManagerField.get(factory)).isNull();

        // Advice chain installed with StatelessRetryOperationsInterceptor
        Advice[] adviceChain = factory.getAdviceChain();
        assertThat(adviceChain).isNotNull().hasSize(1);
        assertThat(adviceChain[0]).isInstanceOf(StatelessRetryOperationsInterceptor.class);

        StatelessRetryOperationsInterceptor retryInterceptor = (StatelessRetryOperationsInterceptor) adviceChain[0];
        Field retryOpsField = StatelessRetryOperationsInterceptor.class.getDeclaredField("retryOperations");
        retryOpsField.setAccessible(true);
        RetryOperations retryOperations = (RetryOperations) retryOpsField.get(retryInterceptor);
        assertThat(retryOperations).isInstanceOf(RetryTemplate.class);

        RetryTemplate retryTemplate = (RetryTemplate) retryOperations;
        RetryPolicy retryPolicy = retryTemplate.getRetryPolicy();
        assertThat(retryPolicy).isNotNull();

        // Verify explicit retry classification:
        // 1. Excluded fatal non-retryable exceptions -> must return false
        assertThat(retryPolicy.shouldRetry(new EventParsingException("bad json"))).isFalse();
        assertThat(retryPolicy.shouldRetry(new AmqpRejectAndDontRequeueException("fatal"))).isFalse();
        assertThat(retryPolicy.shouldRetry(new IllegalArgumentException("invalid arg"))).isFalse();
        assertThat(retryPolicy.shouldRetry(new NullPointerException("null pointer"))).isFalse();

        // Wrapped fatal exception must also be excluded (cause traversal)
        Exception wrappedFatal = new RuntimeException("wrapper", new EventParsingException("nested"));
        assertThat(retryPolicy.shouldRetry(wrappedFatal)).isFalse();

        Exception wrappedAmqpReject = new RuntimeException("wrapper", new AmqpRejectAndDontRequeueException("nested"));
        assertThat(retryPolicy.shouldRetry(wrappedAmqpReject)).isFalse();

        // 2. Approved transient database exceptions -> must return true
        assertThat(retryPolicy.shouldRetry(new CannotAcquireLockException("advisory lock contention"))).isTrue();
        assertThat(retryPolicy.shouldRetry(new PessimisticLockingFailureException("lock timeout"))).isTrue();
        assertThat(retryPolicy.shouldRetry(new QueryTimeoutException("query timeout"))).isTrue();

        Exception wrappedTransient = new RuntimeException("wrapper", new CannotAcquireLockException("nested lock"));
        assertThat(retryPolicy.shouldRetry(wrappedTransient)).isTrue();

        // 3. Unexpected / Non-transient exceptions -> must return false
        assertThat(retryPolicy.shouldRetry(new DataIntegrityViolationException("unique violation"))).isFalse();
        assertThat(retryPolicy.shouldRetry(new IllegalStateException("unexpected state"))).isFalse();
    }

    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalArgumentException("Field " + fieldName + " not found on " + clazz);
    }
}
