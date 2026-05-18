package com.orderflow.outbox;

import com.orderflow.events.EventBroker;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes due outbox events to the configured event broker.
 */
@Service
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final EventBroker eventBroker;
    private final OutboxFailureHandler outboxFailureHandler;

    /**
     * Creates the outbox publisher.
     *
     * @param outboxEventRepository outbox repository
     * @param eventBroker configured broker
     * @param outboxFailureHandler publish failure handler
     */
    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            EventBroker eventBroker,
            OutboxFailureHandler outboxFailureHandler
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventBroker = eventBroker;
        this.outboxFailureHandler = outboxFailureHandler;
    }

    /**
     * Publishes due pending events and records retry metadata for publish failures.
     *
     * @return number of events published
     */
    @Transactional
    public int publishDueEvents() {
        Instant now = Instant.now();
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
        int publishedEvents = 0;

        for (OutboxEvent event : pendingEvents) {
            if (!event.isDue(now)) {
                continue;
            }

            try {
                eventBroker.publish(event.toMessage());
                event.markPublished();
                publishedEvents++;
            } catch (RuntimeException exception) {
                outboxFailureHandler.recordPublishFailure(event, exception);
            }
        }

        return publishedEvents;
    }
}
