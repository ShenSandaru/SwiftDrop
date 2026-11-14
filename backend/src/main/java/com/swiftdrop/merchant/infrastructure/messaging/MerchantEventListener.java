package com.swiftdrop.merchant.infrastructure.messaging;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.merchant.application.InvalidMerchantEventException;
import com.swiftdrop.merchant.application.MerchantEventJson;
import com.swiftdrop.merchant.application.MerchantEventProcessor;

/**
 * Consumes {@code merchant.parcel-status-changed} (SD-410). The body is kept as received, as UTF-8 text, and
 * handed to the processor, whose transaction commits before the container acknowledges the message. A
 * message that is not a valid event is rejected without requeue and reaches the dead-letter queue at once;
 * other failures are retried by the container's retry advice (ADR 0004 §5.3, §6).
 */
class MerchantEventListener {

    private static final Logger log = LoggerFactory.getLogger(MerchantEventListener.class);

    private final MerchantEventJson json;
    private final MerchantEventProcessor processor;

    MerchantEventListener(MerchantEventJson json, MerchantEventProcessor processor) {
        this.json = json;
        this.processor = processor;
    }

    @RabbitListener(queues = MerchantEventQueueConfiguration.QUEUE,
            containerFactory = MerchantEventConsumerConfiguration.CONTAINER_FACTORY)
    public void onMessage(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        ParcelStatusChanged event;
        try {
            event = json.parse(payload);
        } catch (InvalidMerchantEventException e) {
            log.error("Merchant event rejected to the dead-letter queue: messageId={}, reason={}",
                    message.getMessageProperties().getMessageId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException("invalid parcel event", e);
        }
        // Through the Spring proxy, so the processor's transaction applies.
        processor.process(event, payload);
    }
}
