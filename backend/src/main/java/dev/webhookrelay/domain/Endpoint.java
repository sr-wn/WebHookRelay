package dev.webhookrelay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An inspection endpoint. Addressed publicly only by its unguessable {@code slug}.
 * Auto-expires at {@code expiresAt} (TTL cleanup handled by a scheduled sweeper).
 */
@Entity
@Table(name = "endpoint")
@Getter
@Setter
@NoArgsConstructor
public class Endpoint {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id; // UUID

    @Column(nullable = false, unique = true, length = 32)
    private String slug; // crypto-random, non-sequential

    @Column(name = "owner_id", length = 64)
    private String ownerId; // nullable: anonymous endpoints allowed

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
