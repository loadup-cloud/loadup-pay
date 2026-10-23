-- Commit the order hold and exact internal payment command before remote acceptance.
CREATE TABLE order_payment_allocation
(
    id                     VARCHAR(64)  NOT NULL,
    tenant_id              VARCHAR(64)  NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    deleted                TINYINT      NOT NULL DEFAULT 0,
    order_id               VARCHAR(64)  NOT NULL,
    payment_id             VARCHAR(64)  NOT NULL,
    actor_scope            VARCHAR(128) NOT NULL,
    action_key             VARCHAR(128) NOT NULL,
    request_digest         CHAR(64)     NOT NULL,
    caller_scope_hash      CHAR(64)     NOT NULL,
    amount_minor           BIGINT       NOT NULL,
    currency               CHAR(3)      NOT NULL,
    state                  VARCHAR(24)  NOT NULL,
    payment_source_version BIGINT       NOT NULL DEFAULT 0,
    intent_payload         LONGTEXT     NOT NULL,
    payload_hash           CHAR(64)     NOT NULL,
    row_version            BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_allocation_payment (tenant_id, payment_id),
    UNIQUE KEY uk_allocation_action (tenant_id, order_id, actor_scope, action_key),
    KEY ix_allocation_order (tenant_id, order_id, state, payment_id),
    CHECK (amount_minor > 0 AND currency = 'CNY'),
    CHECK (state IN ('INIT', 'HELD', 'CONSUMED', 'RELEASED')),
    CHECK (payment_source_version >= 0 AND row_version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
