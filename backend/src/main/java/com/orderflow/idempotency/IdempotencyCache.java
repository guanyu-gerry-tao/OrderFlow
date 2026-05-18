package com.orderflow.idempotency;

import java.util.Optional;

/**
 * Stores short-lived idempotency responses outside the database.
 */
public interface IdempotencyCache {

    /**
     * Finds a cached response by idempotency key.
     *
     * @param idempotencyKey client-provided idempotency key
     * @return cached response if available
     */
    Optional<CachedIdempotencyResponse> findByKey(String idempotencyKey);

    /**
     * Stores a cached response by idempotency key.
     *
     * @param idempotencyKey client-provided idempotency key
     * @param cachedResponse response to cache
     */
    void put(String idempotencyKey, CachedIdempotencyResponse cachedResponse);
}
