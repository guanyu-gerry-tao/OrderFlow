package com.orderflow.audit;

import com.orderflow.order.OrderStatus;
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
 * Records one visible order workflow state transition.
 */
@Entity
@Table(name = "order_audit_logs")
public class OrderAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private OrderStatus toStatus;

    @Column(nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderAuditLog() {
    }

    /**
     * Creates a new audit log entry for an order transition.
     *
     * @param orderId the order that changed state
     * @param sequenceNumber transition order within the order timeline
     * @param fromStatus the previous state, or null for the initial creation event
     * @param toStatus the new state
     * @param message a short human-readable explanation
     */
    public OrderAuditLog(UUID orderId, int sequenceNumber, OrderStatus fromStatus, OrderStatus toStatus, String message) {
        this.orderId = orderId;
        this.sequenceNumber = sequenceNumber;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.message = message;
        this.createdAt = Instant.now();
    }

    /**
     * Returns the audit log identifier.
     *
     * @return audit log id
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the related order identifier.
     *
     * @return order id
     */
    public UUID getOrderId() {
        return orderId;
    }

    /**
     * Returns the transition order within the order timeline.
     *
     * @return sequence number
     */
    public int getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Returns the previous order status.
     *
     * @return previous status, or null for creation
     */
    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    /**
     * Returns the new order status.
     *
     * @return new status
     */
    public OrderStatus getToStatus() {
        return toStatus;
    }

    /**
     * Returns the transition message.
     *
     * @return message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns when this transition was recorded.
     *
     * @return creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
