package com.orderflow.dlq;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists and queries DLQ events.
 */
public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {

    /**
     * Counts DLQ events by human recovery status.
     *
     * @param status DLQ status
     * @return matching DLQ count
     */
    long countByStatus(DeadLetterStatus status);

    /**
     * Finds a DLQ record for a source outbox event and status.
     *
     * @param outboxEventId source outbox event id
     * @param status DLQ status
     * @return matching DLQ event if present
     */
    Optional<DeadLetterEvent> findByOutboxEventIdAndStatus(UUID outboxEventId, DeadLetterStatus status);
}
