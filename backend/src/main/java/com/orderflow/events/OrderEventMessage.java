package com.orderflow.events;

import java.util.UUID;

/**
 * Serializable event payload sent through the order event topic.
 *
 * @param eventId outbox event identifier
 * @param orderId order aggregate identifier
 * @param eventType workflow event type
 * @param payload JSON payload snapshot
 */
public record OrderEventMessage(
        UUID eventId,
        UUID orderId,
        OrderEventType eventType,
        String payload
) {
}
