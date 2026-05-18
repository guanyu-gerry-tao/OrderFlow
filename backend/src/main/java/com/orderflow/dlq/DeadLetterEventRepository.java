package com.orderflow.dlq;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists and queries DLQ events.
 */
public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {
}
