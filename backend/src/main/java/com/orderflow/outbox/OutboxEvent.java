package com.orderflow.outbox;

import com.orderflow.events.OrderEventMessage;
import com.orderflow.events.OrderEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores one event that must be published after the database transaction commits.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private OrderEventType eventType;

    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected OutboxEvent() {
    }

    /**
     * Creates a pending outbox event.
     *
     * @param aggregateId order identifier
     * @param eventType workflow event type
     * @param payload JSON payload
     */
    public OutboxEvent(UUID aggregateId, OrderEventType eventType, String payload) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Converts this row into a broker message.
     *
     * @return event message
     */
    public OrderEventMessage toMessage() {
        return new OrderEventMessage(id, aggregateId, eventType, payload);
    }

    /**
     * Marks the event as sent to the broker.
     */
    public void markPublished() {
        status = OutboxEventStatus.PUBLISHED;
        publishedAt = Instant.now();
        updatedAt = publishedAt;
        lastError = null;
    }

    /**
     * Marks the event as fully processed by a consumer.
     */
    public void markProcessed() {
        status = OutboxEventStatus.PROCESSED;
        processedAt = Instant.now();
        updatedAt = processedAt;
        lastError = null;
    }

    /**
     * Records a recoverable failure and schedules the next attempt.
     *
     * @param error failure message
     * @param backoff wait time before the next attempt
     */
    public void recordRetry(String error, Duration backoff) {
        retryCount++;
        lastError = trimError(error);
        nextAttemptAt = Instant.now().plus(backoff);
        updatedAt = Instant.now();
    }

    /**
     * Moves the event into DLQ state.
     */
    public void markDeadLettered() {
        status = OutboxEventStatus.DLQ;
        updatedAt = Instant.now();
    }

    /**
     * Requeues a DLQ event for manual replay.
     */
    public void requeueForManualRetry() {
        status = OutboxEventStatus.PUBLISHED;
        retryCount = 0;
        nextAttemptAt = Instant.now();
        lastError = null;
        updatedAt = Instant.now();
    }

    /**
     * Returns true when the event may be attempted now.
     *
     * @param now current time
     * @return whether the event is due
     */
    public boolean isDue(Instant now) {
        return nextAttemptAt == null || !nextAttemptAt.isAfter(now);
    }

    /**
     * Returns the event id.
     *
     * @return id
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the order id.
     *
     * @return aggregate id
     */
    public UUID getAggregateId() {
        return aggregateId;
    }

    /**
     * Returns the event type.
     *
     * @return event type
     */
    public OrderEventType getEventType() {
        return eventType;
    }

    /**
     * Returns the JSON payload.
     *
     * @return payload
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Returns the processing status.
     *
     * @return status
     */
    public OutboxEventStatus getStatus() {
        return status;
    }

    /**
     * Returns the retry count.
     *
     * @return retry count
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * Returns the next attempt time.
     *
     * @return next attempt timestamp
     */
    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    /**
     * Returns the last failure message.
     *
     * @return last error
     */
    public String getLastError() {
        return lastError;
    }

    private String trimError(String error) {
        if (error == null) {
            return "Unknown error";
        }
        if (error.length() <= 500) {
            return error;
        }
        return error.substring(0, 500);
    }
}
