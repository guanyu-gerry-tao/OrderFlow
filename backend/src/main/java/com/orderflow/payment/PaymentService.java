package com.orderflow.payment;

import com.orderflow.order.OrderEntity;
import com.orderflow.failure.FailureInjectionService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Simulates payment authorization for the M1 happy path.
 */
@Service
public class PaymentService {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRequestAttemptRepository paymentRequestAttemptRepository;
    private final FailureInjectionService failureInjectionService;
    private final Clock clock;

    /**
     * Creates a payment service.
     *
     * @param paymentAttemptRepository payment attempt repository
     */
    public PaymentService(
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentRequestAttemptRepository paymentRequestAttemptRepository,
            FailureInjectionService failureInjectionService,
            Clock clock
    ) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentRequestAttemptRepository = paymentRequestAttemptRepository;
        this.failureInjectionService = failureInjectionService;
        this.clock = clock;
    }

    /**
     * Authorizes payment for an order.
     *
     * @param order order to authorize
     */
    public void authorize(OrderEntity order) {
        if (paymentAttemptRepository.findByOrderId(order.getId())
                .stream()
                .anyMatch(paymentAttempt -> paymentAttempt.getStatus() == PaymentStatus.AUTHORIZED)) {
            return;
        }
        failureInjectionService.maybeFailPayment(order.getId());
        paymentAttemptRepository.save(new PaymentAttempt(order.getId()));
    }

    /**
     * Creates a business payment attempt for one pending order.
     *
     * @param orderId order id
     * @param expiresAt payment deadline
     * @return saved payment attempt
     */
    public PaymentAttempt createInitiatedAttempt(java.util.UUID orderId, Instant expiresAt) {
        Instant now = clock.instant();
        PaymentAttempt paymentAttempt = paymentAttemptRepository.saveAndFlush(new PaymentAttempt(orderId, expiresAt, now));
        paymentAttempt.assignIdempotencyKey("authorize:" + paymentAttempt.getId(), now);
        return paymentAttemptRepository.save(paymentAttempt);
    }

    /**
     * Starts one physical confirm request attempt.
     *
     * @param paymentAttempt payment attempt
     * @return saved request attempt
     */
    public PaymentRequestAttempt startRequestAttempt(PaymentAttempt paymentAttempt) {
        return paymentRequestAttemptRepository.save(new PaymentRequestAttempt(
                paymentAttempt.getId(),
                paymentAttempt.getOrderId(),
                paymentAttempt.getIdempotencyKey(),
                clock.instant()
        ));
    }

    /**
     * Finds one payment attempt by id.
     *
     * @param paymentAttemptId payment attempt id
     * @return payment attempt
     */
    public Optional<PaymentAttempt> findAttempt(java.util.UUID paymentAttemptId) {
        return paymentAttemptRepository.findById(paymentAttemptId);
    }

    /**
     * Returns whether gateway timeout should be simulated.
     *
     * @param paymentAttemptId payment attempt id
     * @return whether timeout should be simulated
     */
    public boolean shouldSimulateGatewayTimeout(java.util.UUID paymentAttemptId) {
        return failureInjectionService.consumeGatewayTimeout(paymentAttemptId);
    }

    /**
     * Saves a payment attempt.
     *
     * @param paymentAttempt payment attempt
     * @return saved payment attempt
     */
    public PaymentAttempt save(PaymentAttempt paymentAttempt) {
        return paymentAttemptRepository.save(paymentAttempt);
    }

    /**
     * Saves a request attempt.
     *
     * @param requestAttempt request attempt
     * @return saved request attempt
     */
    public PaymentRequestAttempt save(PaymentRequestAttempt requestAttempt) {
        return paymentRequestAttemptRepository.save(requestAttempt);
    }
}
