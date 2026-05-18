package com.orderflow.outbox;

import com.orderflow.events.OrderEventType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists transactional outbox events.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Counts events by processing status.
     *
     * @param status event status
     * @return matching event count
     */
    long countByStatus(OutboxEventStatus status);

    /**
     * Finds events by processing status.
     *
     * @param status event status
     * @return matching events
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status);

    /**
     * Finds events by event type.
     *
     * @param eventType event type
     * @return matching events
     */
    List<OutboxEvent> findByEventType(OrderEventType eventType);
}
