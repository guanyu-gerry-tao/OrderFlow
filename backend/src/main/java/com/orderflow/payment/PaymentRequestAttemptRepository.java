package com.orderflow.payment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists physical payment request attempts.
 */
public interface PaymentRequestAttemptRepository extends JpaRepository<PaymentRequestAttempt, UUID> {

    /**
     * Finds request attempts for one business payment attempt.
     *
     * @param paymentAttemptId payment attempt id
     * @return request attempts
     */
    List<PaymentRequestAttempt> findByPaymentAttemptIdOrderByCreatedAtAsc(UUID paymentAttemptId);
}
