package com.swiftdrop.merchant.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.dao.QueryTimeoutException;

import com.rabbitmq.client.Channel;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.merchant.application.InvalidMerchantEventException;
import com.swiftdrop.merchant.application.MerchantEventJson;
import com.swiftdrop.merchant.application.MerchantEventProcessor;
import com.swiftdrop.merchant.application.MerchantEventProperties;

/** The listener's rejection of invalid messages and the retry policy (ADR 0004 §5.3, §6, §9). */
class MerchantEventListenerTest {

    private final MerchantEventJson json = new MerchantEventJson();
    private final MerchantEventProcessor processor = mock(MerchantEventProcessor.class);
    private final MerchantEventListener listener = new MerchantEventListener(json, processor);

    private static Message message(String body) {
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).setMessageId("m-1").build();
    }

    @Test
    @DisplayName("A valid event is passed on with the body exactly as received")
    void validEvent() {
        ParcelStatusChanged event = ParcelStatusChanged.of(UUID.randomUUID(), java.time.Instant.now(),
                new ParcelStatusChangedData(UUID.randomUUID(), "SD7K3M9Q2XWB", null, ParcelStatus.CREATED, 1,
                        ParcelSource.MERCHANT, null, UUID.randomUUID(), "ORD-1", null));
        String body = json.toJson(event);

        listener.onMessage(message(body));

        verify(processor).process(event, body);
    }

    @Test
    @DisplayName("Malformed JSON, another event type or schema version is rejected without requeue")
    void invalidMessagesRejected() {
        String valid = json.toJson(ParcelStatusChanged.of(UUID.randomUUID(), java.time.Instant.now(),
                new ParcelStatusChangedData(UUID.randomUUID(), "SD7K3M9Q2XWB", null, ParcelStatus.CREATED, 1,
                        ParcelSource.MERCHANT, null, UUID.randomUUID(), "ORD-1", null)));
        for (String body : new String[] {"not json", "{}", valid.replace("parcel.status-changed", "parcel.renamed"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2")}) {
            assertThatThrownBy(() -> listener.onMessage(message(body)))
                    .as(body)
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                    .hasCauseInstanceOf(InvalidMerchantEventException.class);
        }
        verify(processor, never()).process(any(), anyString());
    }

    @Test
    @DisplayName("Invalid messages and explicit rejections are not retryable, other failures are")
    void retryClassification() {
        assertThat(MerchantEventConsumerConfiguration.isRetryable(new ListenerExecutionFailedException("x",
                new AmqpRejectAndDontRequeueException("bad")))).isFalse();
        assertThat(MerchantEventConsumerConfiguration.isRetryable(new ListenerExecutionFailedException("x",
                new MessageConversionException("bad")))).isFalse();
        assertThat(MerchantEventConsumerConfiguration.isRetryable(new RuntimeException(
                new InvalidMerchantEventException("bad")))).isFalse();
        assertThat(MerchantEventConsumerConfiguration.isRetryable(new ListenerExecutionFailedException("x",
                new QueryTimeoutException("db")))).isTrue();
    }

    @Test
    @DisplayName("A transient failure is tried 3 times in total, then rejected to the dead-letter queue")
    void retriesThenRejects() {
        AtomicInteger calls = new AtomicInteger();
        MethodInterceptor advice = MerchantEventConsumerConfiguration.retryAdvice(fastRetries());

        assertThatThrownBy(() -> advice.invoke(invocation(calls, new QueryTimeoutException("db"))))
                .hasRootCauseInstanceOf(QueryTimeoutException.class)
                .satisfies(e -> assertThat(hasReject(e)).as("rejected without requeue").isTrue());
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("An invalid message is tried once only")
    void invalidNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        MethodInterceptor advice = MerchantEventConsumerConfiguration.retryAdvice(fastRetries());

        assertThatThrownBy(() -> advice.invoke(invocation(calls,
                new AmqpRejectAndDontRequeueException("invalid", new InvalidMerchantEventException("bad")))));
        assertThat(calls.get()).isEqualTo(1);
    }

    private static boolean hasReject(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof AmqpRejectAndDontRequeueException) {
                return true;
            }
        }
        return false;
    }

    private static MerchantEventProperties fastRetries() {
        return new MerchantEventProperties(3, Duration.ofMillis(1), Duration.ofMillis(5), null, null, null, null, null,
                null, null);
    }

    /** The container calls the advice with (channel, message); each proceed() fails like the listener. */
    private static MethodInvocation invocation(AtomicInteger calls, RuntimeException failure) throws Throwable {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[] {mock(Channel.class), message("{}")});
        when(invocation.proceed()).thenAnswer(call -> {
            calls.incrementAndGet();
            throw new ListenerExecutionFailedException("listener failed", failure, message("{}"));
        });
        return invocation;
    }
}
