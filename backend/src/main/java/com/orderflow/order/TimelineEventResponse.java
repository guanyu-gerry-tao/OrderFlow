package com.orderflow.order;

import java.time.Instant;

/**
 * Public API response for one audit timeline event.
 *
 * @param fromStatus previous order status, or null for creation
 * @param toStatus new order status
 * @param message transition message
 * @param createdAt event timestamp
 * @param sequenceNumber transition order within the order timeline
 */
public record TimelineEventResponse(
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String message,
        Instant createdAt,
        int sequenceNumber
) {
}
