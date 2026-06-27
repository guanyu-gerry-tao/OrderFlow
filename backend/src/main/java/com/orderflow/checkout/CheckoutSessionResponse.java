package com.orderflow.checkout;

import com.orderflow.order.OrderResponse;
import com.orderflow.payment.PaymentAttemptResponse;
import com.orderflow.payment.PaymentRequestAttemptStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Public response for checkout state.
 *
 * @param checkoutSessionId checkout session id
 * @param status checkout session status
 * @param order order snapshot
 * @param paymentAttempt active payment attempt snapshot
 * @param requestAttemptId current physical request attempt id
 * @param requestAttemptStatus current request attempt status
 * @param createdAt session creation time
 * @param expiresAt session deadline
 */
public record CheckoutSessionResponse(
        UUID checkoutSessionId,
        CheckoutSessionStatus status,
        OrderResponse order,
        PaymentAttemptResponse paymentAttempt,
        UUID requestAttemptId,
        PaymentRequestAttemptStatus requestAttemptStatus,
        Instant createdAt,
        Instant expiresAt
) {
    /**
     * Returns active payment attempt id for concise tests and clients.
     *
     * @return payment attempt id
     */
    public UUID paymentAttemptId() {
        return paymentAttempt.paymentAttemptId();
    }
}
