package com.orderflow.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed idempotency response cache with safe database fallback on cache failures.
 */
@Component
public class RedisIdempotencyCache implements IdempotencyCache {

    private static final String KEY_PREFIX = "orderflow:idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String cacheMode;
    private final Duration cacheTtl;

    /**
     * Creates a Redis idempotency cache adapter.
     *
     * @param redisTemplate Redis string template
     * @param objectMapper JSON mapper
     * @param cacheMode configured cache mode
     * @param cacheTtl configured cache TTL
     */
    public RedisIdempotencyCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${orderflow.idempotency.cache:redis}") String cacheMode,
            @Value("${orderflow.idempotency.cache-ttl:10m}") Duration cacheTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheMode = cacheMode;
        this.cacheTtl = cacheTtl;
    }

    /**
     * Reads the cached response when Redis is enabled and available.
     *
     * @param idempotencyKey client-provided idempotency key
     * @return cached response if available
     */
    @Override
    public Optional<CachedIdempotencyResponse> findByKey(String idempotencyKey) {
        if (!isRedisEnabled()) {
            return Optional.empty();
        }

        try {
            String cachedValue = redisTemplate.opsForValue().get(cacheKey(idempotencyKey));
            if (cachedValue == null || cachedValue.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(cachedValue, CachedIdempotencyResponse.class));
        } catch (RuntimeException | JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    /**
     * Writes the cached response when Redis is enabled and available.
     *
     * @param idempotencyKey client-provided idempotency key
     * @param cachedResponse response to cache
     */
    @Override
    public void put(String idempotencyKey, CachedIdempotencyResponse cachedResponse) {
        if (!isRedisEnabled()) {
            return;
        }

        try {
            String serializedResponse = objectMapper.writeValueAsString(cachedResponse);
            redisTemplate.opsForValue().set(cacheKey(idempotencyKey), serializedResponse, cacheTtl);
        } catch (RuntimeException | JsonProcessingException exception) {
            // PostgreSQL remains the source of truth when Redis is unavailable.
        }
    }

    private boolean isRedisEnabled() {
        return "redis".equalsIgnoreCase(cacheMode);
    }

    private String cacheKey(String idempotencyKey) {
        return KEY_PREFIX + idempotencyKey;
    }
}
