-- Persist the first close request before any remote fencing or payment closure.
CREATE TABLE order_close_intent
(
    id                VARCHAR(64)  NOT NULL,
    tenant_id         VARCHAR(64)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    order_id          VARCHAR(64)  NOT NULL,
    payee_merchant_id VARCHAR(64)  NOT NULL,
    caller_scope_hash CHAR(64)     NOT NULL,
    request_key       VARCHAR(128) NOT NULL,
    request_digest    CHAR(64)     NOT NULL,
    reason_code       VARCHAR(64)  NOT NULL,
    close_epoch       BIGINT       NOT NULL,
    requested_at      DATETIME(6)  NOT NULL,
    cursor_payment_id VARCHAR(64)  NOT NULL DEFAULT '',
    row_version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_close_order (tenant_id, order_id),
    CHECK (close_epoch > 0 AND row_version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
