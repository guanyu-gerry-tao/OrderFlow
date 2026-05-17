package com.orderflow.audit;

import com.orderflow.order.OrderStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Writes audit records for order state changes.
 */
@Service
public class OrderAuditService {

    private final OrderAuditLogRepository orderAuditLogRepository;

    /**
     * Creates an audit service backed by a JPA repository.
     *
     * @param orderAuditLogRepository audit repository
     */
    public OrderAuditService(OrderAuditLogRepository orderAuditLogRepository) {
        this.orderAuditLogRepository = orderAuditLogRepository;
    }

    /**
     * Records one state transition.
     *
     * @param orderId order identifier
     * @param sequenceNumber transition order within the order timeline
     * @param fromStatus previous state, or null for creation
     * @param toStatus new state
     * @param message short transition explanation
     */
    public void record(UUID orderId, int sequenceNumber, OrderStatus fromStatus, OrderStatus toStatus, String message) {
        orderAuditLogRepository.save(new OrderAuditLog(orderId, sequenceNumber, fromStatus, toStatus, message));
    }
}
