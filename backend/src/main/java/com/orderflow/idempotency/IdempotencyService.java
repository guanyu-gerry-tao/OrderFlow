package com.orderflow.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.api.WorkflowConflictException;
import com.orderflow.order.CreateOrderRequest;
import com.orderflow.order.OrderResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Coordinates request hashing, durable records, Redis cache lookup, and per-key locking.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final IdempotencyCache idempotencyCache;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final String idempotencyMode;

    /**
     * Creates an idempotency service.
     *
     * @param idempotencyRecordRepository durable idempotency repository
     * @param idempotencyCache short-lived response cache
     * @param objectMapper JSON mapper
     * @param jdbcTemplate JDBC helper for PostgreSQL advisory locks
     * @param idempotencyMode configured idempotency mode
     */
    public IdempotencyService(
            IdempotencyRecordRepository idempotencyRecordRepository,
            IdempotencyCache idempotencyCache,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            @Value("${orderflow.idempotency.mode:strict}") String idempotencyMode
    ) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.idempotencyCache = idempotencyCache;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.idempotencyMode = idempotencyMode;
    }

    /**
     * Returns whether strict idempotency should apply to the current request.
     *
     * @param idempotencyKey client-provided key
     * @return true when strict idempotency is enabled and the key is present
     */
    public boolean shouldApply(String idempotencyKey) {
        return "strict".equalsIgnoreCase(idempotencyMode)
                && idempotencyKey != null
                && !idempotencyKey.isBlank();
    }

    /**
     * Normalizes an idempotency key for storage.
     *
     * @param idempotencyKey client-provided key
     * @return normalized key
     */
    public String normalizeKey(String idempotencyKey) {
        return idempotencyKey.trim();
    }

    /**
     * Computes a stable SHA-256 hash for the request body.
     *
     * @param request order creation request
     * @return request hash
     */
    public String hashRequest(CreateOrderRequest request) {
        try {
            String serializedRequest = objectMapper.writeValueAsString(request);
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = messageDigest.digest(serializedRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash idempotent request", exception);
        }
    }

    /**
     * Finds a cached response and verifies the request hash before replaying it.
     *
     * @param idempotencyKey normalized idempotency key
     * @param requestHash current request hash
     * @return reusable response when the cache has a matching request
     */
    public Optional<OrderResponse> findCachedResponse(String idempotencyKey, String requestHash) {
        Optional<CachedIdempotencyResponse> cachedResponse = idempotencyCache.findByKey(idempotencyKey);
        if (cachedResponse.isEmpty()) {
            return Optional.empty();
        }

        ensureSameRequestHash(cachedResponse.get().requestHash(), requestHash);
        return Optional.of(cachedResponse.get().response());
    }

    /**
     * Finds a durable response and verifies the request hash before replaying it.
     *
     * @param idempotencyKey normalized idempotency key
     * @param requestHash current request hash
     * @return reusable response when the database has a matching request
     */
    public Optional<OrderResponse> findStoredResponse(String idempotencyKey, String requestHash) {
        Optional<IdempotencyRecord> idempotencyRecord = idempotencyRecordRepository.findById(idempotencyKey);
        if (idempotencyRecord.isEmpty()) {
            return Optional.empty();
        }

        ensureSameRequestHash(idempotencyRecord.get().getRequestHash(), requestHash);
        OrderResponse response = deserializeResponse(idempotencyRecord.get().getResponseSnapshot());
        idempotencyCache.put(idempotencyKey, new CachedIdempotencyResponse(requestHash, response));
        return Optional.of(response);
    }

    /**
     * Records a completed idempotent order response in PostgreSQL and Redis.
     *
     * @param idempotencyKey normalized idempotency key
     * @param requestHash request hash
     * @param response completed order response
     */
    public void recordCompletedResponse(String idempotencyKey, String requestHash, OrderResponse response) {
        String serializedResponse = serializeResponse(response);
        IdempotencyRecord idempotencyRecord = new IdempotencyRecord(
                idempotencyKey,
                requestHash,
                response.orderId(),
                serializedResponse
        );

        idempotencyRecordRepository.save(idempotencyRecord);
        cacheAfterCommit(idempotencyKey, new CachedIdempotencyResponse(requestHash, response));
    }

    /**
     * Serializes same-key requests with a PostgreSQL transaction-scoped advisory lock.
     *
     * @param idempotencyKey normalized idempotency key
     */
    public void lockKey(String idempotencyKey) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                statement.setString(1, idempotencyKey);
                statement.execute();
            }

            return null;
        });
    }

    private void ensureSameRequestHash(String storedHash, String requestHash) {
        if (!storedHash.equals(requestHash)) {
            throw new WorkflowConflictException("Idempotency key was already used with a different request body");
        }
    }

    private void cacheAfterCommit(String idempotencyKey, CachedIdempotencyResponse cachedResponse) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            idempotencyCache.put(idempotencyKey, cachedResponse);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                idempotencyCache.put(idempotencyKey, cachedResponse);
            }
        });
    }

    private String serializeResponse(OrderResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize idempotent response", exception);
        }
    }

    private OrderResponse deserializeResponse(String responseSnapshot) {
        try {
            return objectMapper.readValue(responseSnapshot, OrderResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize idempotent response", exception);
        }
    }
}
