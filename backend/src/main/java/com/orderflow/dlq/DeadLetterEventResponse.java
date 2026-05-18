package com.orderflow.dlq;

import com.orderflow.events.OrderEventType;
import java.time.Instant;
import java.util.UUID;

/**
 * API response for a DLQ event.
 *
 * @param id DLQ id
 * @param outboxEventId source outbox event id
 * @param orderId related order id
 * @param eventType event type
 * @param retryCount retry count when the event entered DLQ
 * @param lastError last failure message
 * @param status DLQ status
 * @param createdAt DLQ creation time
 * @param replayedAt replay time, if any
 */
public record DeadLetterEventResponse(
        UUID id,
        UUID outboxEventId,
        UUID orderId,
        OrderEventType eventType,
        int retryCount,
        String lastError,
        DeadLetterStatus status,
        Instant createdAt,
        Instant replayedAt
) {
}
