package com.orderflow.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Test broker that records published messages without requiring a live Kafka broker.
 */
@Component
@ConditionalOnProperty(name = "orderflow.events.broker", havingValue = "recording")
public class RecordingEventBroker implements EventBroker {

    private final List<OrderEventMessage> publishedMessages = new ArrayList<>();
    private String nextPublishFailure;

    /**
     * Records or intentionally fails one publish attempt.
     *
     * @param message order event message
     */
    @Override
    public synchronized void publish(OrderEventMessage message) {
        if (nextPublishFailure != null) {
            String failure = nextPublishFailure;
            nextPublishFailure = null;
            throw new IllegalStateException(failure);
        }

        publishedMessages.add(message);
    }

    /**
     * Makes the next publish attempt fail with a controlled error.
     *
     * @param error failure message
     */
    public synchronized void failNextPublish(String error) {
        nextPublishFailure = error;
    }

    /**
     * Returns the recorded messages.
     *
     * @return published messages
     */
    public synchronized List<OrderEventMessage> publishedMessages() {
        return Collections.unmodifiableList(new ArrayList<>(publishedMessages));
    }

    /**
     * Clears recorded messages and configured failures.
     */
    public synchronized void clear() {
        publishedMessages.clear();
        nextPublishFailure = null;
    }
}
