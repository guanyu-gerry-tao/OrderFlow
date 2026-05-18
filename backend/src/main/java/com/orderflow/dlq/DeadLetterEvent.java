package com.orderflow.dlq;

import com.orderflow.events.OrderEventType;
import com.orderflow.outbox.OutboxEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores an event that exhausted retries and needs human-visible recovery.
 */
@Entity
@Table(name = "dead_letter_events")
public class DeadLetterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "outbox_event_id", nullable = false)
    private UUID outboxEventId;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private OrderEventType eventType;

    @Column(nullable = false)
    private String payload;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", nullable = false)
    private String lastError;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeadLetterStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "replayed_at")
    private Instant replayedAt;

    protected DeadLetterEvent() {
    }

    /**
     * Creates a DLQ record from an outbox event.
     *
     * @param event failed outbox event
     */
    public DeadLetterEvent(OutboxEvent event) {
        this.outboxEventId = event.getId();
        this.aggregateId = event.getAggregateId();
        this.eventType = event.getEventType();
        this.payload = event.getPayload();
        this.retryCount = event.getRetryCount();
        this.lastError = event.getLastError();
        this.status = DeadLetterStatus.OPEN;
        this.createdAt = Instant.now();
    }

    /**
     * Marks this DLQ record as replayed.
     */
    public void markReplayed() {
        status = DeadLetterStatus.REPLAYED;
        replayedAt = Instant.now();
    }

    /**
     * Returns the DLQ id.
     *
     * @return id
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the source outbox event id.
     *
     * @return outbox event id
     */
    public UUID getOutboxEventId() {
        return outboxEventId;
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
     * Returns the last failure message.
     *
     * @return last error
     */
    public String getLastError() {
        return lastError;
    }
}
