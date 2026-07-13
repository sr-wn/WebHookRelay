package dev.webhookrelay.service;

import dev.webhookrelay.config.WebhookRelayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis-backed distributed token-bucket rate limiter. State (tokens + last-refill
 * timestamp) lives in Redis, so the limit is enforced globally across all app
 * instances — a stateless-app requirement. A Lua script makes the
 * read-refill-deduct-write atomic, so concurrent requests on different instances
 * can't both "see" the same token.
 *
 * <p>Two independent buckets are checked per request: one per slug (protects a
 * single endpoint from being spammed) and one per source IP (protects the
 * platform from a single abusive client).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimiter {

    private static final RedisScript<List> BUCKET_SCRIPT = new DefaultRedisScript<>(
            """
            local data = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            if tokens == nil then
              tokens = capacity
              ts = now
            end
            local delta = math.max(0, now - ts)
            local refilled = math.min(capacity, tokens + (delta / 1000.0) * rate)
            local allowed = 0
            if refilled >= requested then
              refilled = refilled - requested
              allowed = 1
            end
            redis.call('HMSET', KEYS[1], 'tokens', refilled, 'ts', now)
            redis.call('EXPIRE', KEYS[1], math.ceil(capacity / rate) + 10)
            return { allowed, refilled }
            """,
            List.class);

    private final StringRedisTemplate redis;
    private final WebhookRelayProperties props;

    public boolean allowSlug(String slug) {
        return allow("rl:slug:" + slug,
                props.rateLimit().perSlugBurst(), props.rateLimit().perSlugPerSecond());
    }

    public boolean allowIp(String ip) {
        return allow("rl:ip:" + ip,
                props.rateLimit().perIpBurst(), props.rateLimit().perIpPerSecond());
    }

    private boolean allow(String key, int capacity, double refillPerSecond) {
        try {
            List<?> result = redis.execute(BUCKET_SCRIPT, List.of(key),
                    capacity, refillPerSecond, System.currentTimeMillis(), 1);
            if (result == null || result.isEmpty()) {
                return true; // fail open rather than block on a Redis hiccup
            }
            // With StringRedisTemplate the Lua integers may deserialize as Strings.
            Object allowed = result.get(0);
            long value = allowed instanceof Number n
                    ? n.longValue() : Long.parseLong(allowed.toString().trim());
            return value == 1L;
        } catch (RuntimeException e) {
            log.warn("Rate-limit check failed for key={}, allowing", key, e);
            return true;
        }
    }

    /** Pure refill math (extracted for unit testing the bucket algorithm). */
    static double refill(double tokens, long lastMs, long nowMs, double ratePerSec, double capacity) {
        double deltaSec = Math.max(0, (nowMs - lastMs) / 1000.0);
        return Math.min(capacity, tokens + deltaSec * ratePerSec);
    }
}
