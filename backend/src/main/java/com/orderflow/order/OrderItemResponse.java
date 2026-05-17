package com.orderflow.order;

import java.util.UUID;

/**
 * Public API response for one order line item.
 *
 * @param itemId line item identifier
 * @param sku stock keeping unit
 * @param quantity ordered units
 */
public record OrderItemResponse(
        UUID itemId,
        String sku,
        int quantity
) {
}
