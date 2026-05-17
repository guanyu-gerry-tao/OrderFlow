package com.orderflow.payment;

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
 * Stores one simulated payment authorization attempt.
 */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentAttempt() {
    }

    /**
     * Creates an authorized payment attempt for an order.
     *
     * @param orderId order identifier
     */
    public PaymentAttempt(UUID orderId) {
        this.orderId = orderId;
        this.status = PaymentStatus.AUTHORIZED;
        this.createdAt = Instant.now();
    }

    /**
     * Returns the payment attempt id.
     *
     * @return payment attempt id
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the order identifier.
     *
     * @return order id
     */
    public UUID getOrderId() {
        return orderId;
    }

    /**
     * Returns the simulated payment status.
     *
     * @return payment status
     */
    public PaymentStatus getStatus() {
        return status;
    }
}
