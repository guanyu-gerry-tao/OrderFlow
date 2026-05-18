package com.orderflow.events;

import com.orderflow.failure.FailureInjectionService;
import com.orderflow.outbox.OutboxEvent;
import com.orderflow.outbox.OutboxEventRepository;
import com.orderflow.outbox.OutboxEventStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Consumes published order events and applies retry/DLQ handling.
 */
@Service
public class OrderEventConsumer {

    private final OutboxEventRepository outboxEventRepository;
    private final OrderEventProcessor orderEventProcessor;
    private final OrderEventFailureHandler orderEventFailureHandler;
    private final FailureInjectionService failureInjectionService;

    /**
     * Creates the order event consumer.
     *
     * @param outboxEventRepository outbox repository
     * @param orderEventProcessor event processor
     * @param orderEventFailureHandler failure handler
     * @param failureInjectionService failure injection service
     */
    public OrderEventConsumer(
            OutboxEventRepository outboxEventRepository,
            OrderEventProcessor orderEventProcessor,
            OrderEventFailureHandler orderEventFailureHandler,
            FailureInjectionService failureInjectionService
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.orderEventProcessor = orderEventProcessor;
        this.orderEventFailureHandler = orderEventFailureHandler;
        this.failureInjectionService = failureInjectionService;
    }

    /**
     * Processes all due published events.
     *
     * @return number of events processed successfully
     */
    public int processDuePublishedEvents() {
        Instant now = Instant.now();
        List<OutboxEvent> events = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PUBLISHED);
        int processedEvents = 0;

        for (OutboxEvent event : events) {
            if (!event.isDue(now)) {
                continue;
            }
            if (processEventById(event.getId())) {
                processedEvents++;
            }
        }

        return processedEvents;
    }

    /**
     * Processes a specific event and records retry metadata if it fails.
     *
     * @param eventId event id
     * @return whether the event processed successfully
     */
    public boolean processEventById(UUID eventId) {
        try {
            failureInjectionService.maybeFailConsumer(eventId);
            orderEventProcessor.processEvent(eventId);
            return true;
        } catch (RuntimeException exception) {
            orderEventFailureHandler.recordConsumerFailure(eventId, exception);
            return false;
        }
    }
}
