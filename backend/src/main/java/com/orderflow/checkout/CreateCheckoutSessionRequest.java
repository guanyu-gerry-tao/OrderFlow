package com.orderflow.checkout;

import com.orderflow.order.CreateOrderItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request body for starting checkout.
 *
 * @param customerId customer identifier
 * @param items checkout line items
 */
public record CreateCheckoutSessionRequest(
        @NotBlank String customerId,
        @NotEmpty List<@NotNull @Valid CreateOrderItemRequest> items
) {
}
