package com.orderflow.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Public response for one business payment attempt.
 *
 * @param paymentAttemptId payment attempt id
 * @param idempotencyKey authorize operation idempotency key
 * @param status current payment attempt status
 * @param expiresAt payment attempt deadline
 */
public record PaymentAttemptResponse(
        UUID paymentAttemptId,
        String idempotencyKey,
        PaymentStatus status,
        Instant expiresAt
) {
}
