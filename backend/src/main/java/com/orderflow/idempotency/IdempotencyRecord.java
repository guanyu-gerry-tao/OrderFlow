package com.orderflow.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores the durable source of truth for completed idempotent order requests.
 */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "response_snapshot", nullable = false)
    private String responseSnapshot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IdempotencyRecord() {
    }

    /**
     * Creates a durable idempotency record.
     *
     * @param idempotencyKey client-provided key
     * @param requestHash canonical request hash
     * @param orderId logical order id
     * @param responseSnapshot serialized response snapshot
     */
    public IdempotencyRecord(
            String idempotencyKey,
            String requestHash,
            UUID orderId,
            String responseSnapshot
    ) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.orderId = orderId;
        this.responseSnapshot = responseSnapshot;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Returns the idempotency key.
     *
     * @return idempotency key
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Returns the request hash.
     *
     * @return request hash
     */
    public String getRequestHash() {
        return requestHash;
    }

    /**
     * Returns the logical order id.
     *
     * @return order id
     */
    public UUID getOrderId() {
        return orderId;
    }

    /**
     * Returns the serialized response snapshot.
     *
     * @return response snapshot JSON
     */
    public String getResponseSnapshot() {
        return responseSnapshot;
    }
}
