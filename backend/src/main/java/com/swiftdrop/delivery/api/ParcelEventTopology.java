package com.swiftdrop.delivery.api;

/**
 * Where {@code delivery} publishes parcel events in RabbitMQ (ADR 0004 §5). {@code delivery} declares
 * the exchange; each consumer declares and binds its own queue with these names.
 */
public final class ParcelEventTopology {

    /** Durable topic exchange owned by {@code delivery}. */
    public static final String EXCHANGE = "swiftdrop.events";

    /** Routing key of {@link ParcelStatusChanged} events: the event type. */
    public static final String STATUS_CHANGED_ROUTING_KEY = ParcelStatusChanged.EVENT_TYPE;

    private ParcelEventTopology() {
    }
}
