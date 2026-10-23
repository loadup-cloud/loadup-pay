-- The deadline change and its immutable request receipt commit in one order transaction.
CREATE TABLE order_extension_receipt
(
    id                     VARCHAR(64)  NOT NULL,
    tenant_id              VARCHAR(64)  NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    deleted                TINYINT      NOT NULL DEFAULT 0,
    order_id               VARCHAR(64)  NOT NULL,
    caller_scope_hash      CHAR(64)     NOT NULL,
    request_key            VARCHAR(128) NOT NULL,
    request_digest         CHAR(64)     NOT NULL,
    original_due_at        DATETIME(6)  NOT NULL,
    previous_due_at        DATETIME(6)  NOT NULL,
    requested_due_at       DATETIME(6)  NOT NULL,
    expected_order_version BIGINT       NOT NULL,
    deadline_version       BIGINT       NOT NULL,
    applied_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_extension_request (tenant_id, order_id, request_key),
    UNIQUE KEY uk_order_extension_deadline (tenant_id, order_id, deadline_version),
    CHECK (expected_order_version >= 0 AND deadline_version > 1),
    CHECK (previous_due_at >= original_due_at AND requested_due_at > previous_due_at
        AND requested_due_at > applied_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
