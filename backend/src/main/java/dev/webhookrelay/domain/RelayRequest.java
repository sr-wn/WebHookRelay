package dev.webhookrelay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * A single captured HTTP request against an endpoint.
 * Headers and query params are stored as JSON columns because payload shape is arbitrary.
 */
@Entity
@Table(name = "relay_request")
@Getter
@Setter
@NoArgsConstructor
public class RelayRequest {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id; // UUID

    @Column(name = "endpoint_slug", nullable = false, length = 32)
    private String endpointSlug;

    @Column(nullable = false, length = 10)
    private String method;

    // No columnDefinition: let the dialect pick the JSON type (MySQL json / Postgres jsonb).
    // Actual column type is owned by the vendor-specific Flyway migration.
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> headers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_params")
    private Map<String, Object> queryParams;

    @Column(name = "body_content_type", length = 128)
    private String bodyContentType;

    /** Body captured as text (possibly truncated). LONGVARCHAR -> LONGTEXT (MySQL) / text (Postgres). */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String body;

    @Column(name = "body_truncated", nullable = false)
    private boolean bodyTruncated;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
