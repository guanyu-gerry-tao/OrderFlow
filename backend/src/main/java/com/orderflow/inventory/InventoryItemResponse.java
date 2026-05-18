package com.orderflow.inventory;

import java.time.Instant;

/**
 * Represents one inventory row in the operations console API.
 *
 * @param sku stock keeping unit
 * @param availableQuantity currently available units
 * @param version optimistic locking version
 * @param updatedAt last update time
 */
public record InventoryItemResponse(
        String sku,
        int availableQuantity,
        int version,
        Instant updatedAt
) {
}
