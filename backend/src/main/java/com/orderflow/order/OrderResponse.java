package com.orderflow.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public API response for an order.
 *
 * @param orderId order identifier
 * @param customerId customer identifier
 * @param status current workflow status
 * @param items order line items
 * @param createdAt creation time
 * @param updatedAt last update time
 */
public record OrderResponse(
        UUID orderId,
        String customerId,
        OrderStatus status,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
