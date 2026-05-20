package com.orderflow.outbox;

import com.orderflow.config.ConditionalOnRuntimeRole;
import com.orderflow.config.RuntimeRole;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically publishes due outbox events in the reliable event mode.
 */
@Component
@ConditionalOnRuntimeRole(RuntimeRole.WORKER)
public class OutboxPublisherScheduler {

    private final EventMode eventMode;
    private final OutboxPublisher outboxPublisher;

    /**
     * Creates the publisher scheduler.
     *
     * @param eventMode event mode
     * @param outboxPublisher outbox publisher
     */
    public OutboxPublisherScheduler(EventMode eventMode, OutboxPublisher outboxPublisher) {
        this.eventMode = eventMode;
        this.outboxPublisher = outboxPublisher;
    }

    /**
     * Publishes due events on a fixed delay.
     */
    @Scheduled(
            initialDelayString = "${orderflow.events.publisher-initial-delay:2000}",
            fixedDelayString = "${orderflow.events.publisher-interval:2000}"
    )
    public void publishDueEvents() {
        if (eventMode.isOutboxKafka()) {
            outboxPublisher.publishDueEvents();
        }
    }
}
