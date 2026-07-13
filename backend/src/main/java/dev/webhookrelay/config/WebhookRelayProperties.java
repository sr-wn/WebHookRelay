package dev.webhookrelay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Externalized config. All values come from application-*.yml / env vars — never hardcoded.
 */
@ConfigurationProperties(prefix = "webhookrelay")
public record WebhookRelayProperties(
        Duration defaultTtl,
        int maxBodyBytes,
        Duration endpointCacheTtl,
        RateLimit rateLimit,
        Jwt jwt
) {
    /**
     * Token-bucket params. {@code burst} = max tokens a bucket can hold (instant
     * capacity); {@code perSecond} = sustained refill rate. e.g. burst 120 / 2.0 per
     * second ≈ 120 requests/min with burst tolerance.
     */
    public record RateLimit(
            int perSlugBurst, double perSlugPerSecond,
            int perIpBurst, double perIpPerSecond) {}

    /**
     * JWT signing/verification config. The secret must be >= 32 bytes for HS256.
     * Inherited by every profile; supply a real secret via WEBHOOKRELAY_JWT_SECRET
     * in staging/prod (never commit it).
     */
    public record Jwt(String secret, Duration tokenTtl) {}
}
