package com.orderflow.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists and queries order audit timeline entries.
 */
public interface OrderAuditLogRepository extends JpaRepository<OrderAuditLog, UUID> {

    /**
     * Finds all audit entries for one order in timeline order.
     *
     * @param orderId order identifier
     * @return ordered audit entries
     */
    List<OrderAuditLog> findByOrderIdOrderBySequenceNumberAsc(UUID orderId);
}
