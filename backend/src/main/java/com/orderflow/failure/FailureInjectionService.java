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
        consumerFailuresByEventId.clear();
    }
}
