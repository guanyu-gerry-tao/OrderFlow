package com.orderflow.payment;

import com.orderflow.order.OrderEntity;
import com.orderflow.failure.FailureInjectionService;
import org.springframework.stereotype.Service;

/**
 * Simulates payment authorization for the M1 happy path.
 */
@Service
public class PaymentService {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final FailureInjectionService failureInjectionService;

    /**
     * Creates a payment service.
     *
     * @param paymentAttemptRepository payment attempt repository
     */
    public PaymentService(
            PaymentAttemptRepository paymentAttemptRepository,
            FailureInjectionService failureInjectionService
    ) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.failureInjectionService = failureInjectionService;
    }

    /**
     * Authorizes payment for an order.
     *
     * @param order order to authorize
     */
    public void authorize(OrderEntity order) {
        failureInjectionService.maybeFailPayment(order.getId());
        paymentAttemptRepository.save(new PaymentAttempt(order.getId()));
    }
}
