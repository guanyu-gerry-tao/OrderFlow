package com.orderflow.checkout;

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
 * Stores a checkout session that owns one pending order and active payment attempt.
 */
@Entity
@Table(name = "checkout_sessions")
public class CheckoutSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "active_payment_attempt_id", nullable = false)
    private UUID activePaymentAttemptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckoutSessionStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CheckoutSession() {
    }

    /**
     * Creates an active checkout session.
     *
     * @param orderId order id
     * @param activePaymentAttemptId active payment attempt id
     * @param expiresAt session expiration
     */
    public CheckoutSession(UUID orderId, UUID activePaymentAttemptId, Instant expiresAt) {
        this(orderId, activePaymentAttemptId, expiresAt, Instant.now());
    }

    /**
     * Creates an active checkout session with an explicit timestamp.
     *
     * @param orderId order id
     * @param activePaymentAttemptId active payment attempt id
     * @param expiresAt session expiration
     * @param createdAt creation timestamp
     */
    public CheckoutSession(UUID orderId, UUID activePaymentAttemptId, Instant expiresAt, Instant createdAt) {
        this.orderId = orderId;
        this.activePaymentAttemptId = activePaymentAttemptId;
        this.expiresAt = expiresAt;
        this.status = CheckoutSessionStatus.ACTIVE;
        this.createdAt = createdAt;
        this.updatedAt = this.createdAt;
    }

    /**
     * Marks the session confirmed.
     */
    public void confirm() {
        confirm(Instant.now());
    }

    /**
     * Marks the session confirmed.
     *
     * @param updatedAt update timestamp
     */
    public void confirm(Instant updatedAt) {
        this.status = CheckoutSessionStatus.CONFIRMED;
        this.updatedAt = updatedAt;
    }

    /**
     * Marks the session expired.
     */
    public void expire() {
        expire(Instant.now());
    }

    /**
     * Marks the session expired.
     *
     * @param updatedAt update timestamp
     */
    public void expire(Instant updatedAt) {
        this.status = CheckoutSessionStatus.EXPIRED;
        this.updatedAt = updatedAt;
    }

    /**
     * Returns checkout session id.
     *
     * @return checkout session id
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns order id.
     *
     * @return order id
     */
    public UUID getOrderId() {
        return orderId;
    }

    /**
     * Returns active payment attempt id.
     *
     * @return active payment attempt id
     */
    public UUID getActivePaymentAttemptId() {
        return activePaymentAttemptId;
    }

    /**
     * Returns checkout session status.
     *
     * @return status
     */
    public CheckoutSessionStatus getStatus() {
        return status;
    }

    /**
     * Returns session expiration.
     *
     * @return expiration timestamp
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Returns creation timestamp.
     *
     * @return creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
