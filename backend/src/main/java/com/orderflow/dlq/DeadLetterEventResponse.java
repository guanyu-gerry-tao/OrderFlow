package com.orderflow.dlq;

import com.orderflow.events.OrderEventType;
import java.util.UUID;

/**
 * API response for a DLQ event.
 *
 * @param id DLQ id
 * @param outboxEventId source outbox event id
 * @param orderId related order id
 * @param eventType event type
 * @param lastError last failure message
 */
public record DeadLetterEventResponse(
        UUID id,
        UUID outboxEventId,
        UUID orderId,
        OrderEventType eventType,
        String lastError
) {
}
