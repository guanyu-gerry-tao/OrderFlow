package com.orderflow.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * One requested order line item.
 *
 * @param sku stock keeping unit
 * @param quantity requested units
 */
public record CreateOrderItemRequest(
        @NotBlank String sku,
        @Min(1) int quantity
) {
}
