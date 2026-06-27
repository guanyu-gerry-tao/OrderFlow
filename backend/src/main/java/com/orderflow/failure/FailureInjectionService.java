package com.orderflow.failure;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Provides controlled failure injection for reliability tests and demos.
 */
@Service
public class FailureInjectionService {

    private final Map<UUID, Integer> paymentFailuresByOrderId = new HashMap<>();
    private final Map<UUID, Integer> gatewayTimeoutsByPaymentAttemptId = new HashMap<>();
    private final Map<UUID, String> consumerFailuresByEventId = new HashMap<>();

    /**
     * Configures payment authorization failures for one order.
     *
     * @param orderId order id
     * @param attempts number of attempts to fail
     */
    public synchronized void failPaymentAttempts(UUID orderId, int attempts) {
        paymentFailuresByOrderId.put(orderId, attempts);
    }

    /**
     * Configures one consumer crash for one event.
     *
     * @param eventId outbox event id
     * @param message failure message
     */
    public synchronized void failConsumerOnce(UUID eventId, String message) {
        consumerFailuresByEventId.put(eventId, message);
    }

    /**
     * Configures one simulated payment gateway response timeout.
     *
     * @param paymentAttemptId payment attempt id
     */
    public synchronized void failNextPaymentGatewayResponse(UUID paymentAttemptId) {
        gatewayTimeoutsByPaymentAttemptId.put(paymentAttemptId, 1);
    }

    /**
     * Throws an injected payment timeout when configured.
     *
     * @param orderId order id
     */
    public synchronized void maybeFailPayment(UUID orderId) {
        Integer remainingFailures = paymentFailuresByOrderId.get(orderId);
        if (remainingFailures == null || remainingFailures <= 0) {
            return;
        }

        if (remainingFailures == 1) {
            paymentFailuresByOrderId.remove(orderId);
        } else {
            paymentFailuresByOrderId.put(orderId, remainingFailures - 1);
        }
        throw new IllegalStateException("Injected payment timeout");
    }

    /**
     * Returns whether the next payment gateway response should time out.
     *
     * @param paymentAttemptId payment attempt id
     * @return whether timeout should be simulated
     */
    public synchronized boolean consumeGatewayTimeout(UUID paymentAttemptId) {
        Integer remainingFailures = gatewayTimeoutsByPaymentAttemptId.get(paymentAttemptId);
        if (remainingFailures == null || remainingFailures <= 0) {
            return false;
        }

        if (remainingFailures == 1) {
            gatewayTimeoutsByPaymentAttemptId.remove(paymentAttemptId);
        } else {
            gatewayTimeoutsByPaymentAttemptId.put(paymentAttemptId, remainingFailures - 1);
        }
        return true;
    }

    /**
     * Throws an injected consumer crash when configured.
     *
     * @param eventId outbox event id
     */
    public synchronized void maybeFailConsumer(UUID eventId) {
        String failure = consumerFailuresByEventId.remove(eventId);
        if (failure != null) {
            throw new IllegalStateException(failure);
        }
    }

    /**
     * Clears all configured failures.
     */
    public synchronized void clear() {
        paymentFailuresByOrderId.clear();
        gatewayTimeoutsByPaymentAttemptId.clear();
        consumerFailuresByEventId.clear();
    }
}
