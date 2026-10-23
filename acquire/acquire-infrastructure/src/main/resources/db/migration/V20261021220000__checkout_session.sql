-- Order-scoped checkout capabilities. Raw tokens and signing keys are never persisted.

CREATE TABLE checkout_session
(
    id                VARCHAR(64)  NOT NULL,
    tenant_id         VARCHAR(64)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    order_id          VARCHAR(64)  NOT NULL,
    payee_merchant_id VARCHAR(64)  NOT NULL,
    request_key       VARCHAR(128) NOT NULL,
    request_digest    CHAR(64)     NOT NULL,
    token_hash        CHAR(64)     NOT NULL,
    token_nonce       CHAR(64)     NOT NULL,
    token_key_ref     VARCHAR(128) NOT NULL,
    token_key_version VARCHAR(64)  NOT NULL,
    state             VARCHAR(24)  NOT NULL,
    expires_at        DATETIME(6)  NOT NULL,
    allowed_origin    VARCHAR(512) NULL,
    scope_snapshot    LONGTEXT     NOT NULL,
    row_version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_request (tenant_id, order_id, request_key),
    UNIQUE KEY uk_session_token (token_hash),
    KEY ix_session_order (tenant_id, order_id, state),
    CHECK (state IN ('INIT', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    CHECK (row_version >= 0 AND MICROSECOND(expires_at) = 0),
    CHECK (allowed_origin IS NOT NULL)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
