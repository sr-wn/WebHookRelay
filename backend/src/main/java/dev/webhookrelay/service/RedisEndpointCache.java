package dev.webhookrelay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.webhookrelay.domain.Endpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Short-TTL Redis cache for hot endpoint lookups.
 *
 * <p>Every captured webhook does an active-endpoint lookup. At any real volume that
 * would hammer MySQL on the hot path, so we serve it from Redis instead. The cache is
 * the only thing that knows about Redis here — {@link EndpointService} just asks it.
 *
 * <p>Two keys per slug:
 * <ul>
 *   <li>a serialized {@link Endpoint} when one exists and is unexpired, and</li>
 *   <li>a negative sentinel ({@code ∅}) when no active endpoint exists, so we don't
 *       stampede the DB for unknown/swept slugs either.</li>
 * </ul>
 * Negative entries use the same TTL; they're cheap to invalidate on create.
 *
 * <p>Staleness is bounded by the TTL. Even a cached-but-now-expired entry is re-checked
 * against {@code expiresAt} locally before being trusted, so an endpoint can never be
 * served past its TTL. This is a deliberate, cache-aside design: Redis is a speed layer,
 * MySQL remains the source of truth.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisEndpointCache {

    private static final String KEY_PREFIX = "ep:active:";
    private static final String NEGATIVE = "∅";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** Returns the cached active endpoint, an empty Optional (negative cached), or
     *  {@code null} on a cache miss so the caller knows to load from the DB. */
    Optional<Endpoint> get(String slug) {
        String raw = redis.opsForValue().get(KEY_PREFIX + slug);
        if (raw == null) {
            return null; // miss
        }
        if (NEGATIVE.equals(raw)) {
            return Optional.empty(); // negative cached
        }
        try {
            return Optional.of(objectMapper.readValue(raw, Endpoint.class));
        } catch (Exception e) {
            log.warn("Corrupt endpoint cache entry for slug={}, dropping", slug, e);
            redis.delete(KEY_PREFIX + slug);
            return null;
        }
    }

    void put(String slug, Optional<Endpoint> endpoint, Duration ttl) {
        String key = KEY_PREFIX + slug;
        try {
            String value = endpoint.map(e -> {
                try {
                    return objectMapper.writeValueAsString(e);
                } catch (Exception ex) {
                    return NEGATIVE;
                }
            }).orElse(NEGATIVE);
            redis.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Failed to write endpoint cache for slug={}", slug, e);
        }
    }

    void evict(String slug) {
        redis.delete(KEY_PREFIX + slug);
    }

    /** True when the cached entry is still within its TTL and not past its own expiry. */
    static boolean isStillActive(Optional<Endpoint> cached, Instant now) {
        return cached.isPresent() && !cached.get().isExpired(now);
    }
}
