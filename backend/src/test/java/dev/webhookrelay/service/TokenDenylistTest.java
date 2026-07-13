package dev.webhookrelay.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenDenylistTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    @Captor ArgumentCaptor<Duration> ttlCaptor;

    private TokenDenylist denylist;

    @BeforeEach
    void setup() {
        denylist = new TokenDenylist(redis);
    }

    @Test
    void revokeStoresKeyWithTtlUntilExpiry() {
        when(redis.opsForValue()).thenReturn(ops);
        Instant expiry = Instant.now().plusSeconds(120);
        denylist.revoke("jti-1", expiry);

        verify(ops).set(eq("jwt:revoked:jti-1"), eq("1"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue().getSeconds()).isBetween(119L, 121L);
    }

    @Test
    void isRevokedDelegatesToRedis() {
        when(redis.hasKey("jwt:revoked:jti-2")).thenReturn(true);
        assertThat(denylist.isRevoked("jti-2")).isTrue();
        when(redis.hasKey("jwt:revoked:jti-3")).thenReturn(false);
        assertThat(denylist.isRevoked("jti-3")).isFalse();
    }

    @Test
    void revokeIgnoresAlreadyExpiredToken() {
        denylist.revoke("jti-x", Instant.now().minusSeconds(10));
        verify(redis, never()).opsForValue();
    }
}
