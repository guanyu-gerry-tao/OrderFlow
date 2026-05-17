package com.orderflow.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for seeding one inventory item.
 *
 * @param sku stock keeping unit
 * @param availableQuantity available units to store
 */
public record SeedInventoryRequest(
        @NotBlank String sku,
        @Min(0) int availableQuantity
) {
}
