package com.orderflow.events;

import com.orderflow.dlq.DeadLetterEvent;
import com.orderflow.dlq.DeadLetterEventRepository;
import com.orderflow.outbox.OutboxEvent;
import com.orderflow.outbox.OutboxEventRepository;
import com.orderflow.outbox.RetryPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records consumer failures, retry metadata, and DLQ records.
 */
@Service
public class OrderEventFailureHandler {

    private final OutboxEventRepository outboxEventRepository;
    private final DeadLetterEventRepository deadLetterEventRepository;
    private final RetryPolicy retryPolicy;

    /**
     * Creates the failure handler.
     *
     * @param outboxEventRepository outbox repository
     * @param deadLetterEventRepository DLQ repository
     * @param retryPolicy retry policy
     */
    public OrderEventFailureHandler(
            OutboxEventRepository outboxEventRepository,
            DeadLetterEventRepository deadLetterEventRepository,
            RetryPolicy retryPolicy
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.deadLetterEventRepository = deadLetterEventRepository;
        this.retryPolicy = retryPolicy;
    }

    /**
     * Records a failed consumer attempt.
     *
     * @param eventId failed event id
     * @param exception failure
     */
    @Transactional
    public void recordConsumerFailure(java.util.UUID eventId, RuntimeException exception) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found"));
        int nextRetryCount = event.getRetryCount() + 1;
        event.recordRetry(exception.getMessage(), retryPolicy.backoffFor(nextRetryCount));

        if (!retryPolicy.allowsRetry(event.getRetryCount())) {
            event.markDeadLettered();
            deadLetterEventRepository.save(new DeadLetterEvent(event));
        }
    }
}
