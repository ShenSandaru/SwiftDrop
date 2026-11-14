package com.swiftdrop.merchant.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.rabbitmq.client.GetResponse;
import com.swiftdrop.delivery.api.ParcelStatusChanged;

/**
 * The merchant event queue on real RabbitMQ 4.3 (ADR 0004 §5.1, SD-501): RabbitMQ accepts the quorum queue
 * with at-least-once dead-lettering, parcel events reach it through the delivery exchange, and a message
 * rejected without requeue lands in the dead-letter queue. The outbox relay stays off; messages are
 * published directly.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
class MerchantEventQueueIT {

    private static final String RABBIT_USER = "swiftdrop-test";
    private static final String RABBIT_PASSWORD = "swiftdrop-test-only";
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Container
    static GenericContainer<?> rabbit = new GenericContainer<>("rabbitmq:4.3-management-alpine")
            .withEnv("RABBITMQ_DEFAULT_USER", RABBIT_USER)
            .withEnv("RABBITMQ_DEFAULT_PASS", RABBIT_PASSWORD)
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbit.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> RABBIT_USER);
        registry.add("spring.rabbitmq.password", () -> RABBIT_PASSWORD);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AmqpAdmin admin;

    @AfterEach
    void purge() {
        admin.purgeQueue(MerchantEventQueueConfiguration.QUEUE, false);
        admin.purgeQueue(MerchantEventQueueConfiguration.DEAD_LETTER_QUEUE, false);
    }

    @Test
    void brokerAcceptsTheQuorumQueuesWithAtLeastOnceDeadLettering() throws Exception {
        // RabbitAdmin declares on the first connection; force it.
        rabbitTemplate.execute(channel -> null);

        String flags = rabbit.execInContainer("rabbitmqctl", "list_feature_flags", "--quiet").getStdout();
        assertThat(flags).containsPattern("stream_queue\\s+enabled");

        String queues = rabbit.execInContainer("rabbitmqctl", "list_queues", "--quiet", "name", "type", "arguments")
                .getStdout();
        String source = lineOf(queues, MerchantEventQueueConfiguration.QUEUE + "\t");
        assertThat(source).contains("quorum", "at-least-once", "reject-publish", "swiftdrop.dead-letter",
                "x-delivery-limit", "x-max-length");
        assertThat(lineOf(queues, MerchantEventQueueConfiguration.DEAD_LETTER_QUEUE + "\t")).contains("quorum");
    }

    @Test
    void parcelEventsPublishedToTheExchangeReachTheMerchantQueue() {
        rabbitTemplate.send(ParcelStatusChanged.EXCHANGE, ParcelStatusChanged.EVENT_TYPE, message("e-1"));

        Message received = rabbitTemplate.receive(MerchantEventQueueConfiguration.QUEUE, WAIT.toMillis());
        assertThat(received).isNotNull();
        assertThat(new String(received.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"eventId\":\"e-1\"}");
    }

    @Test
    void aMessageRejectedWithoutRequeueIsDeadLettered() {
        rabbitTemplate.send(ParcelStatusChanged.EXCHANGE, ParcelStatusChanged.EVENT_TYPE, message("e-2"));
        Boolean rejected = null;
        long deadline = System.currentTimeMillis() + WAIT.toMillis();
        while (rejected == null && System.currentTimeMillis() < deadline) {
            rejected = rabbitTemplate.execute(channel -> {
                GetResponse response = channel.basicGet(MerchantEventQueueConfiguration.QUEUE, false);
                if (response == null) {
                    return null;
                }
                channel.basicReject(response.getEnvelope().getDeliveryTag(), false);
                return true;
            });
        }
        assertThat(rejected).as("message taken from the merchant queue").isTrue();

        Message deadLetter = rabbitTemplate.receive(MerchantEventQueueConfiguration.DEAD_LETTER_QUEUE, WAIT.toMillis());
        assertThat(deadLetter).as("message in the dead-letter queue").isNotNull();
        assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"eventId\":\"e-2\"}");
        assertThat(deadLetter.getMessageProperties().getReceivedRoutingKey())
                .isEqualTo(MerchantEventQueueConfiguration.QUEUE);
    }

    private static Message message(String eventId) {
        return MessageBuilder.withBody(("{\"eventId\":\"" + eventId + "\"}").getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setMessageId(eventId)
                .build();
    }

    private static String lineOf(String table, String prefix) {
        return table.lines().filter(line -> line.startsWith(prefix)).findFirst()
                .orElseThrow(() -> new AssertionError("no queue line starting with " + prefix + " in:\n" + table));
    }
}
