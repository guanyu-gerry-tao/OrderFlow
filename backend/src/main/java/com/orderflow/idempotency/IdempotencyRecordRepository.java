package com.orderflow.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists durable idempotency records.
 */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
}
