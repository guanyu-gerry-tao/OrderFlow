package com.orderflow.events;

import com.orderflow.outbox.EventMode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically retries due consumer failures recorded in the outbox table.
 */
@Component
public class OrderEventRetryScheduler {

    private final EventMode eventMode;
    private final OrderEventConsumer orderEventConsumer;

    /**
     * Creates the consumer retry scheduler.
     *
     * @param eventMode event mode
     * @param orderEventConsumer order event consumer
     */
    public OrderEventRetryScheduler(EventMode eventMode, OrderEventConsumer orderEventConsumer) {
        this.eventMode = eventMode;
        this.orderEventConsumer = orderEventConsumer;
    }

    /**
     * Retries due published events that previously failed in the consumer.
     *
     * @return number of events processed successfully
     */
    @Scheduled(
            initialDelayString = "${orderflow.events.consumer-retry-initial-delay:2000}",
            fixedDelayString = "${orderflow.events.consumer-retry-interval:2000}"
    )
    public int retryDueEvents() {
        if (!eventMode.isOutboxKafka()) {
            return 0;
        }

        return orderEventConsumer.processDuePublishedEvents();
    }
}
