package com.orderflow.idempotency;

import com.orderflow.order.OrderResponse;

/**
 * Serializable response cached for a completed idempotent request.
 *
 * @param requestHash canonical request hash
 * @param response order response snapshot
 */
public record CachedIdempotencyResponse(String requestHash, OrderResponse response) {
}
