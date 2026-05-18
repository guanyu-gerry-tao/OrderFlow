package com.orderflow.events;

/**
 * Publishes order events to the configured broker.
 */
public interface EventBroker {

    /**
     * Publishes one order event message.
     *
     * @param message order event message
     */
    void publish(OrderEventMessage message);
}
