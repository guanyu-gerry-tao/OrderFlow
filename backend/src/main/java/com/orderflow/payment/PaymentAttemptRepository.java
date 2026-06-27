package com.orderflow.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists and queries simulated payment attempts.
 */
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    /**
     * Finds payment attempts for one order.
     *
     * @param orderId order identifier
     * @return payment attempts for the order
     */
    List<PaymentAttempt> findByOrderId(UUID orderId);

    /**
     * Finds a payment attempt by idempotency key.
     *
     * @param idempotencyKey idempotency key
     * @return matching payment attempt
     */
    Optional<PaymentAttempt> findByIdempotencyKey(String idempotencyKey);
}
