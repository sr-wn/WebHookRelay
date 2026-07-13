CREATE TABLE endpoint (
    id          VARCHAR(36)  NOT NULL,
    slug        VARCHAR(32)  NOT NULL,
    owner_id    VARCHAR(64)  NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    expires_at  TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_endpoint_slug (slug),
    KEY idx_endpoint_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE relay_request (
    id                VARCHAR(36)  NOT NULL,
    endpoint_slug     VARCHAR(32)  NOT NULL,
    method            VARCHAR(10)  NOT NULL,
    headers           JSON         NULL,
    query_params      JSON         NULL,
    body_content_type VARCHAR(128) NULL,
    body              LONGTEXT     NULL,
    body_truncated    BOOLEAN      NOT NULL DEFAULT FALSE,
    source_ip         VARCHAR(64)  NULL,
    received_at       TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_relay_request_slug_received (endpoint_slug, received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
