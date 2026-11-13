package com.swiftdrop.notification.application;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.notification.infrastructure.NotificationAmqpConfiguration;

/**
 * RabbitMQ message listener for {@code parcel.status-changed} events (ADR 0004 §5, §11).
 * Consumes messages from {@code notification.parcel-status-changed} using the dedicated
 * container factory, parses raw JSON payloads via {@link ParcelStatusEventParser}, and
 * delegates transactional domain processing to {@link NotificationEventProcessor}.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final ParcelStatusEventParser parser;
    private final NotificationEventProcessor processor;

    public NotificationEventListener(ParcelStatusEventParser parser, NotificationEventProcessor processor) {
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.processor = Objects.requireNonNull(processor, "processor must not be null");
    }

    /**
     * Consumes an incoming AMQP message from the notification queue.
     * <p>
     * Exceptions are not caught here; they propagate to the container factory's retry interceptor
     * and dead-letter recoverer according to the verified retry classification.
     *
     * @param message the raw RabbitMQ message
     */
    @RabbitListener(
            queues = NotificationAmqpConfiguration.NOTIFICATION_QUEUE,
            containerFactory = "notificationRabbitListenerContainerFactory"
    )
    public void onParcelStatusChanged(Message message) {
        Objects.requireNonNull(message, "message must not be null");
        String rawPayload = new String(message.getBody(), StandardCharsets.UTF_8);

        log.debug("Received message from queue {}: messageId={}",
                NotificationAmqpConfiguration.NOTIFICATION_QUEUE,
                message.getMessageProperties() != null ? message.getMessageProperties().getMessageId() : null);

        ParcelStatusChanged event = parser.parse(rawPayload);
        processor.process(event, rawPayload);
    }
}

