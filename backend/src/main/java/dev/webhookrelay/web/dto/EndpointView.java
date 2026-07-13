package dev.webhookrelay.web.dto;

import dev.webhookrelay.domain.Endpoint;

import java.time.Instant;

public record EndpointView(String id, String slug, Instant createdAt, Instant expiresAt) {
    public static EndpointView of(Endpoint e) {
        return new EndpointView(e.getId(), e.getSlug(), e.getCreatedAt(), e.getExpiresAt());
    }
}
