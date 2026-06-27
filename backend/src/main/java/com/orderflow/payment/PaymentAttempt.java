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

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "request_hash")
    private String requestHash;

    @Column(name = "response_snapshot")
    private String responseSnapshot;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

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
        this.updatedAt = this.createdAt;
    }

    /**
     * Creates a business payment attempt waiting for authorization.
     *
     * @param orderId order identifier
     * @param expiresAt authorization deadline
     */
    public PaymentAttempt(UUID orderId, Instant expiresAt) {
        this(orderId, expiresAt, Instant.now());
    }

    /**
     * Creates a business payment attempt waiting for authorization.
     *
     * @param orderId order identifier
     * @param expiresAt authorization deadline
     * @param createdAt creation timestamp
     */
    public PaymentAttempt(UUID orderId, Instant expiresAt, Instant createdAt) {
        this.orderId = orderId;
        this.status = PaymentStatus.INITIATED;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Assigns the payment idempotency key after the attempt id is known.
     *
     * @param idempotencyKey operation idempotency key
     */
    public void assignIdempotencyKey(String idempotencyKey) {
        assignIdempotencyKey(idempotencyKey, Instant.now());
    }

    /**
     * Assigns the payment idempotency key after the attempt id is known.
     *
     * @param idempotencyKey operation idempotency key
     * @param updatedAt update timestamp
     */
    public void assignIdempotencyKey(String idempotencyKey, Instant updatedAt) {
        this.idempotencyKey = idempotencyKey;
        this.updatedAt = updatedAt;
    }

    /**
     * Marks the attempt as authorized and stores the first response snapshot.
     *
     * @param requestHash opaque request fingerprint
     * @param responseSnapshot response snapshot
     */
    public void authorize(String requestHash, String responseSnapshot) {
        authorize(requestHash, responseSnapshot, Instant.now());
    }

    /**
     * Marks the attempt as authorized and stores the first response snapshot.
     *
     * @param requestHash opaque request fingerprint
     * @param responseSnapshot response snapshot
     * @param updatedAt update timestamp
     */
    public void authorize(String requestHash, String responseSnapshot, Instant updatedAt) {
        this.status = PaymentStatus.AUTHORIZED;
        this.requestHash = requestHash;
        this.responseSnapshot = responseSnapshot;
        this.updatedAt = updatedAt;
    }

    /**
     * Marks the attempt as expired.
     */
    public void expire() {
        expire(Instant.now());
    }

    /**
     * Marks the attempt as expired.
     *
     * @param updatedAt update timestamp
     */
    public void expire(Instant updatedAt) {
        this.status = PaymentStatus.EXPIRED;
        this.updatedAt = updatedAt;
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

    /**
     * Returns the idempotency key for the authorize operation.
     *
     * @return idempotency key
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Returns the stored request hash.
     *
     * @return request hash
     */
    public String getRequestHash() {
        return requestHash;
    }

    /**
     * Returns the stored response snapshot.
     *
     * @return response snapshot
     */
    public String getResponseSnapshot() {
        return responseSnapshot;
    }

    /**
     * Returns the payment attempt deadline.
     *
     * @return expiration timestamp
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }
}
