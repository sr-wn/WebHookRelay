package dev.webhookrelay.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Distributed denylist for JWTs. A stateless token is normally valid until it expires,
 * so a leaked token can't be undone. We close that gap with a Redis set of revoked
 * {@code jti}s: revocation is therefore enforced on <em>every</em> instance (the denylist
 * is shared state, unlike the token itself), and the entry auto-expires with the token
 * so the set never needs manual cleanup.
 *
 * <p>Key: {@code jwt:revoked:<jti>}, TTL = remaining token lifetime.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenDenylist {

    private static final String KEY_PREFIX = "jwt:revoked:";

    private final StringRedisTemplate redis;

    /** Revoke a token identified by {@code jti} until {@code tokenExpiry}. No-op (but
     *  logged) if the token is already expired — nothing to denylist. */
    public void revoke(String jti, Instant tokenExpiry) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), tokenExpiry);
        if (ttl.isNegative() || ttl.isZero()) {
            log.debug("Not denylisting already-expired token jti={}", jti);
            return;
        }
        redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }
}
