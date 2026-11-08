package com.swiftdrop.delivery.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.domain.OutboxEvent;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * Topology and relay against real RabbitMQ 4.3 and PostgreSQL 18 (SD-501/502): the ADR 0004 broker
 * prerequisites and queue arguments, a status change travelling from the outbox to the merchant queue,
 * at-least-once dead-lettering, and returns for unroutable messages.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password=",
        "swiftdrop.outbox.relay.enabled=true",
        "swiftdrop.outbox.relay.poll-interval=PT0.5S",
        "spring.rabbitmq.dynamic=true"
})
@Testcontainers
class OutboxRelayIT {

    private static final String MERCHANT_QUEUE = "merchant.parcel-status-changed";
    private static final String MERCHANT_DLQ = MERCHANT_QUEUE + ".dlq";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4.3-management-alpine");

    @Autowired
    private ParcelCreation parcelCreation;
    @Autowired
    private RabbitTemplate rabbit;
    @Autowired
    private AmqpAdmin amqpAdmin;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FeeCalculator feeCalculator;

    @BeforeEach
    void setUp() {
        when(feeCalculator.calculateFee(any(), any(), any(), anyInt())).thenReturn(new FeeQuote(
                new BigDecimal("650.00"), "LKR", new BigDecimal("400.00"), new BigDecimal("150.00"),
                new BigDecimal("100.00"), UUID.randomUUID()));
        ((RabbitAdmin) amqpAdmin).initialize(); // declare the topology now rather than on first use
    }

    @Test
    void brokerHasTheStreamQueueFlagAndAcceptsTheQueueArguments() throws Exception {
        String flags = rabbitmq.execInContainer("rabbitmqctl", "-q", "list_feature_flags", "name", "state").getStdout();
        assertThat(flags).containsPattern("stream_queue\\s+enabled");

        String queues = rabbitmq.execInContainer("rabbitmqctl", "-q", "list_queues", "name", "type", "arguments")
                .getStdout();
        String merchantQueue = queues.lines().filter(line -> line.startsWith(MERCHANT_QUEUE + "\t")).findFirst()
                .orElseThrow();
        assertThat(merchantQueue)
                .contains("quorum")
                .contains("x-dead-letter-exchange", "swiftdrop.dead-letter")
                .contains("x-dead-letter-routing-key", MERCHANT_QUEUE)
                .contains("x-dead-letter-strategy", "at-least-once")
                .contains("x-overflow", "reject-publish")
                .contains("x-delivery-limit", "x-max-length");
        String deadLetterQueue = queues.lines().filter(line -> line.startsWith(MERCHANT_DLQ + "\t")).findFirst()
                .orElseThrow();
        assertThat(deadLetterQueue).contains("quorum").doesNotContain("x-dead-letter-exchange", "x-max-length");
    }

    @Test
    void statusChangeTravelsFromTheOutboxToTheMerchantQueue() {
        CreatedParcel parcel = parcelCreation.createMerchantParcel(order("ORD-" + UUID.randomUUID()));
        UUID eventId = jdbc.queryForObject("SELECT event_id FROM parcel_outbox_events WHERE parcel_id = ?",
                UUID.class, parcel.parcelId());

        Message message = waitFor(() -> receiveFromMerchantQueue(eventId.toString()), Duration.ofSeconds(20));

        MessageProperties properties = message.getMessageProperties();
        assertThat(properties.getMessageId()).isEqualTo(eventId.toString());
        assertThat(properties.getType()).isEqualTo("parcel.status-changed");
        assertThat(properties.getContentType()).isEqualTo("application/json");
        assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getReceivedRoutingKey()).isEqualTo("parcel.status-changed");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains(parcel.parcelId().toString(), "\"newStatus\"", "CREATED")
                .doesNotContain("Nimal", "+9477");
        Instant publishedAt = waitFor(() -> jdbc.queryForObject(
                "SELECT published_at FROM parcel_outbox_events WHERE event_id = ?", Instant.class, eventId),
                Duration.ofSeconds(10));
        assertThat(publishedAt).isNotNull();
    }

    @Test
    void aRejectedMessageIsDeadLetteredToTheDlq() {
        String messageId = "dead-letter-" + UUID.randomUUID();
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(messageId);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        rabbit.send("", MERCHANT_QUEUE, new Message("{}".getBytes(StandardCharsets.UTF_8), properties));

        // Reject it without requeue, as listeners will for failures (ADR 0004 §5.3)
        boolean rejected = waitFor(() -> rabbit.execute(channel -> {
            var response = channel.basicGet(MERCHANT_QUEUE, false);
            if (response == null) {
                return null;
            }
            boolean ours = messageId.equals(response.getProps().getMessageId());
            if (ours) {
                channel.basicReject(response.getEnvelope().getDeliveryTag(), false);
            } else {
                channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
            }
            return ours ? Boolean.TRUE : null;
        }), Duration.ofSeconds(10));
        assertThat(rejected).isTrue();

        Message deadLetter = waitFor(() -> {
            Message received = rabbit.receive(MERCHANT_DLQ, 1000);
            return received != null && messageId.equals(received.getMessageProperties().getMessageId())
                    ? received : null;
        }, Duration.ofSeconds(15));
        Map<String, Object> headers = deadLetter.getMessageProperties().getHeaders();
        assertThat(headers).containsEntry("x-first-death-queue", MERCHANT_QUEUE)
                .containsEntry("x-first-death-reason", "rejected");
    }

    @Test
    void unroutableMessagesAreReturnedAndRoutedOnesConfirmed() {
        OutboxPublisher publisher = new OutboxPublisher(rabbit, Duration.ofSeconds(5));

        OutboxEvent unrouted = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), 1, "test.unrouted", "{}",
                Instant.now());
        assertThat(publisher.publish(unrouted)).isEqualTo(OutboxPublisher.Result.RETURNED);

        OutboxEvent routed = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), 1, "parcel.status-changed", "{}",
                Instant.now());
        assertThat(publisher.publish(routed)).isEqualTo(OutboxPublisher.Result.CONFIRMED);
    }

    /** Takes messages until the one with {@code messageId} arrives; others are left out of the way. */
    private Message receiveFromMerchantQueue(String messageId) {
        Message message = rabbit.receive(MERCHANT_QUEUE, 1000);
        return message != null && messageId.equals(message.getMessageProperties().getMessageId()) ? message : null;
    }

    private static <T> T waitFor(Supplier<T> attempt, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            T value = attempt.get();
            if (value != null) {
                return value;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Condition not met within " + timeout);
    }

    private static NewMerchantParcel order(String reference) {
        UUID zone = UUID.randomUUID();
        DeliveryContact pickup = new DeliveryContact("Store", "+94771234567", "1 Main St", null, "Colombo", zone, null);
        DeliveryContact dropoff = new DeliveryContact("Nimal", "+94779876543", "2 Hill Rd", null, "Kandy", zone, null);
        return new NewMerchantParcel(UUID.randomUUID(), reference, pickup, dropoff, ParcelSize.MEDIUM, 1200, null);
    }
}
