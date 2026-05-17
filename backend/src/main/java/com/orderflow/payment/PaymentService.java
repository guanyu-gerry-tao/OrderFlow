package com.orderflow.payment;

import com.orderflow.order.OrderEntity;
import org.springframework.stereotype.Service;

/**
 * Simulates payment authorization for the M1 happy path.
 */
@Service
public class PaymentService {

    private final PaymentAttemptRepository paymentAttemptRepository;

    /**
     * Creates a payment service.
     *
     * @param paymentAttemptRepository payment attempt repository
     */
    public PaymentService(PaymentAttemptRepository paymentAttemptRepository) {
        this.paymentAttemptRepository = paymentAttemptRepository;
    }

    /**
     * Authorizes payment for an order.
     *
     * @param order order to authorize
     */
    public void authorize(OrderEntity order) {
        paymentAttemptRepository.save(new PaymentAttempt(order.getId()));
    }
}
