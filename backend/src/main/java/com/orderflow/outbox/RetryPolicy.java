package com.orderflow.outbox;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Calculates retry limits and exponential backoff for event processing.
 */
@Component
public class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialBackoff;

    /**
     * Creates the retry policy from Spring configuration.
     *
     * @param maxAttempts maximum attempts before DLQ
     * @param initialBackoff first retry delay
     */
    public RetryPolicy(
            @Value("${orderflow.events.retry.max-attempts:3}") int maxAttempts,
            @Value("${orderflow.events.retry.initial-backoff:5s}") Duration initialBackoff
    ) {
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
    }

    /**
     * Returns true when another retry is allowed.
     *
     * @param retryCount current retry count after a failed attempt
     * @return whether retry is still allowed
     */
    public boolean allowsRetry(int retryCount) {
        return retryCount < maxAttempts;
    }

    /**
     * Calculates exponential backoff for the next retry.
     *
     * @param nextRetryCount retry count after the failed attempt
     * @return backoff duration
     */
    public Duration backoffFor(int nextRetryCount) {
        long multiplier = 1L;
        for (int index = 1; index < nextRetryCount; index++) {
            multiplier = multiplier * 2L;
        }
        return initialBackoff.multipliedBy(multiplier);
    }
}
