CREATE TABLE endpoint (
    id          VARCHAR(36)  NOT NULL,
    slug        VARCHAR(32)  NOT NULL,
    owner_id    VARCHAR(64)  NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    expires_at  TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_endpoint_slug UNIQUE (slug)
);
CREATE INDEX idx_endpoint_expires_at ON endpoint (expires_at);

CREATE TABLE relay_request (
    id                VARCHAR(36)  NOT NULL,
    endpoint_slug     VARCHAR(32)  NOT NULL,
    method            VARCHAR(10)  NOT NULL,
    headers           JSONB        NULL,
    query_params      JSONB        NULL,
    body_content_type VARCHAR(128) NULL,
    body              TEXT         NULL,
    body_truncated    BOOLEAN      NOT NULL DEFAULT FALSE,
    source_ip         VARCHAR(64)  NULL,
    received_at       TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_relay_request_slug_received ON relay_request (endpoint_slug, received_at);
