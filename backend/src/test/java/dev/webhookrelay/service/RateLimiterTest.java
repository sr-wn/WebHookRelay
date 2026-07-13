package dev.webhookrelay.service;

import dev.webhookrelay.config.WebhookRelayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    @Mock StringRedisTemplate redis;
    @Mock WebhookRelayProperties props;
    @Mock WebhookRelayProperties.RateLimit rateLimit;

    private RateLimiter limiter;

    @BeforeEach
    void setup() {
        lenient().when(props.rateLimit()).thenReturn(rateLimit);
        lenient().when(rateLimit.perSlugBurst()).thenReturn(10);
        lenient().when(rateLimit.perSlugPerSecond()).thenReturn(2.0);
        lenient().when(rateLimit.perIpBurst()).thenReturn(20);
        lenient().when(rateLimit.perIpPerSecond()).thenReturn(5.0);
        limiter = new RateLimiter(redis, props);
    }

    @Test
    void allowsWhenBucketHasTokens() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(1L, 1.0));
        assertThat(limiter.allowSlug("s")).isTrue();
    }

    @Test
    void deniesWhenBucketEmpty() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(0L, 0.0));
        assertThat(limiter.allowSlug("s")).isFalse();
    }

    @Test
    void failsOpenOnRedisError() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));
        assertThat(limiter.allowSlug("s")).isTrue();
    }

    @Test
    void refillMath() {
        assertThat(RateLimiter.refill(0, 0, 1000, 2.0, 10)).isEqualTo(2.0);
        assertThat(RateLimiter.refill(10, 0, 1000, 2.0, 10)).isEqualTo(10.0); // capped at capacity
        assertThat(RateLimiter.refill(5, 0, 0, 2.0, 10)).isEqualTo(5.0);      // no elapsed time
    }
}
