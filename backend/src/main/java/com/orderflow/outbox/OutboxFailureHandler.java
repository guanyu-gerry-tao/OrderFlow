package com.orderflow.outbox;

import com.orderflow.config.ConditionalOnRuntimeRole;
import com.orderflow.config.RuntimeRole;
import com.orderflow.dlq.DeadLetterEvent;
import com.orderflow.dlq.DeadLetterEventRepository;
import org.springframework.stereotype.Service;

/**
 * Applies retry and DLQ handling for outbox publish failures.
 */
@Service
@ConditionalOnRuntimeRole(RuntimeRole.WORKER)
public class OutboxFailureHandler {

    private final DeadLetterEventRepository deadLetterEventRepository;
    private final RetryPolicy retryPolicy;

    /**
     * Creates the outbox failure handler.
     *
     * @param deadLetterEventRepository DLQ repository
     * @param retryPolicy retry policy
     */
    public OutboxFailureHandler(
            DeadLetterEventRepository deadLetterEventRepository,
            RetryPolicy retryPolicy
    ) {
        this.deadLetterEventRepository = deadLetterEventRepository;
        this.retryPolicy = retryPolicy;
    }

    /**
     * Records one publish failure and moves exhausted events to DLQ.
     *
     * @param event failed outbox event
     * @param exception publish exception
     */
    public void recordPublishFailure(OutboxEvent event, RuntimeException exception) {
        int nextRetryCount = event.getRetryCount() + 1;
        event.recordRetry(exception.getMessage(), retryPolicy.backoffFor(nextRetryCount));

        if (!retryPolicy.allowsRetry(event.getRetryCount())) {
            event.markDeadLettered();
            deadLetterEventRepository.save(new DeadLetterEvent(event));
        }
    }
}
