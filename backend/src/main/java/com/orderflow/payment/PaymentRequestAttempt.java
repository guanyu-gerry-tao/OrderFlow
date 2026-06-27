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
 * Records one physical payment confirm request for observability.
 */
@Entity
@Table(name = "payment_request_attempts")
public class PaymentRequestAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_attempt_id", nullable = false)
    private UUID paymentAttemptId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentRequestAttemptStatus status;

    @Column
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PaymentRequestAttempt() {
    }

    /**
     * Creates an initiated request attempt.
     *
     * @param paymentAttemptId business payment attempt id
     * @param orderId order id
     * @param idempotencyKey payment idempotency key
     */
    public PaymentRequestAttempt(UUID paymentAttemptId, UUID orderId, String idempotencyKey) {
        this(paymentAttemptId, orderId, idempotencyKey, Instant.now());
    }

    /**
     * Creates an initiated request attempt.
     *
     * @param paymentAttemptId business payment attempt id
     * @param orderId order id
     * @param idempotencyKey payment idempotency key
     * @param createdAt creation timestamp
     */
    public PaymentRequestAttempt(UUID paymentAttemptId, UUID orderId, String idempotencyKey, Instant createdAt) {
        this.paymentAttemptId = paymentAttemptId;
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentRequestAttemptStatus.INITIATED;
        this.createdAt = createdAt;
    }

    /**
     * Marks the request attempt complete.
     *
     * @param status final status
     * @param message result message
     */
    public void complete(PaymentRequestAttemptStatus status, String message) {
        complete(status, message, Instant.now());
    }

    /**
     * Marks the request attempt complete.
     *
     * @param status final status
     * @param message result message
     * @param completedAt completion timestamp
     */
    public void complete(PaymentRequestAttemptStatus status, String message, Instant completedAt) {
        this.status = status;
        this.message = message;
        this.completedAt = completedAt;
    }

    /**
     * Returns request attempt id.
     *
     * @return request attempt id
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns business payment attempt id.
     *
     * @return payment attempt id
     */
    public UUID getPaymentAttemptId() {
        return paymentAttemptId;
    }

    /**
     * Returns current request attempt status.
     *
     * @return status
     */
    public PaymentRequestAttemptStatus getStatus() {
        return status;
    }
}
