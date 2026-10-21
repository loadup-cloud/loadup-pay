-- DESIGN DRAFT ONLY. Actual initial migration is owned by limitcenter-infrastructure.
-- First seven tables align with V20261021180000; execution grants use V20261021190000; final two tables remain future design.

CREATE TABLE limit_policy_head
(
    id                VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted           TINYINT     NOT NULL DEFAULT 0,
    policy_id         VARCHAR(64) NOT NULL,
    payee_merchant_id VARCHAR(64) NOT NULL,
    latest_version    INT         NOT NULL DEFAULT 0,
    row_version       BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_limit_policy_head (tenant_id, policy_id),
    CHECK (latest_version >= 0 AND row_version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE limit_policy_revision
(
    id                VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted           TINYINT     NOT NULL DEFAULT 0,
    policy_id         VARCHAR(64) NOT NULL,
    policy_version    INT         NOT NULL,
    policy_hash       CHAR(64)    NOT NULL,
    payee_merchant_id VARCHAR(64) NOT NULL,
    compiled_rules    LONGTEXT    NOT NULL,
    activated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_limit_policy_version (tenant_id, policy_id, policy_version)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE limit_rule_runtime
(
    id                  VARCHAR(64) NOT NULL,
    tenant_id           VARCHAR(64) NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    deleted             TINYINT     NOT NULL DEFAULT 0,
    owner_policy_id     VARCHAR(64) NOT NULL,
    policy_version      INT         NOT NULL,
    rule_scope_id       VARCHAR(64) NOT NULL,
    merchant_id         VARCHAR(64) NOT NULL,
    metric              VARCHAR(24) NOT NULL,
    currency            CHAR(3)     NOT NULL,
    window_type         VARCHAR(16) NOT NULL,
    week_start          TINYINT     NOT NULL DEFAULT 1,
    timezone            VARCHAR(64) NOT NULL,
    current_limit       BIGINT      NOT NULL,
    enforcement_enabled TINYINT     NOT NULL DEFAULT 1,
    metering_enabled    TINYINT     NOT NULL DEFAULT 1,
    row_version         BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_limit_runtime_scope (tenant_id, rule_scope_id, merchant_id, metric, currency, window_type),
    CHECK (current_limit >= 0 AND week_start BETWEEN 1 AND 7 AND metering_enabled = 1 AND policy_version > 0),
    CHECK (currency = 'CNY' AND timezone = 'Asia/Shanghai')
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE limit_bucket
(
    id            VARCHAR(64) NOT NULL,
    tenant_id     VARCHAR(64) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    deleted       TINYINT     NOT NULL DEFAULT 0,
    rule_scope_id VARCHAR(64) NOT NULL,
    merchant_id   VARCHAR(64) NOT NULL,
    metric        VARCHAR(24) NOT NULL,
    currency      CHAR(3)     NOT NULL,
    window_type   VARCHAR(16) NOT NULL,
    window_start  DATETIME(6) NOT NULL,
    window_end    DATETIME(6) NOT NULL,
    used_value    BIGINT      NOT NULL DEFAULT 0,
    held_value    BIGINT      NOT NULL DEFAULT 0,
    row_version   BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_limit_bucket (tenant_id, rule_scope_id, merchant_id, metric, currency, window_type, window_start,
                                window_end),
    KEY ix_bucket_merchant (tenant_id, merchant_id, window_type, window_start),
    CHECK (used_value >= 0 AND held_value >= 0),
    CHECK (window_start < window_end)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE limit_operation_gate
(
    id                        VARCHAR(64) NOT NULL,
    tenant_id                 VARCHAR(64) NOT NULL,
    created_at                DATETIME(6) NOT NULL,
    updated_at                DATETIME(6) NOT NULL,
    deleted                   TINYINT     NOT NULL DEFAULT 0,
    operation_id              VARCHAR(64) NOT NULL,
    payment_id                VARCHAR(64) NOT NULL,
    state                     VARCHAR(24) NOT NULL,
    payment_source_version    BIGINT      NOT NULL DEFAULT 0,
    no_collection_evidence_id VARCHAR(64) NULL,
    row_version               BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_limit_gate_operation (tenant_id, operation_id),
    UNIQUE KEY uk_limit_gate_payment (tenant_id, payment_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE limit_reservation
(
    id                     VARCHAR(64) NOT NULL,
    tenant_id              VARCHAR(64) NOT NULL,
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    deleted                TINYINT     NOT NULL DEFAULT 0,
    operation_id           VARCHAR(64) NOT NULL,
    payment_id             VARCHAR(64) NOT NULL,
    order_id               VARCHAR(64) NOT NULL,
    payee_merchant_id      VARCHAR(64) NOT NULL,
    amount_minor           BIGINT      NOT NULL,
    currency               CHAR(3)     NOT NULL,
    policy_id              VARCHAR(64) NOT NULL,
    policy_version         INT         NOT NULL,
    policy_hash            CHAR(64)    NOT NULL,
    request_digest         CHAR(64)    NOT NULL,
    caller_service_id      VARCHAR(64) NOT NULL,
    initiated_at           DATETIME(6) NOT NULL,
    state                  VARCHAR(24) NOT NULL,
    reserve_expires_at     DATETIME(6) NOT NULL,
    execution_epoch        BIGINT      NOT NULL DEFAULT 0,
    active_attempt_id      VARCHAR(64) NULL,
    payment_source_version BIGINT      NOT NULL DEFAULT 0,
    row_version            BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_limit_operation (tenant_id, operation_id),
    UNIQUE KEY uk_limit_payment (tenant_id, payment_id),
    KEY ix_reservation_expire (tenant_id, state, reserve_expires_at, id),
    CHECK (amount_minor > 0 AND currency = 'CNY' AND policy_version > 0 AND reserve_expires_at > initiated_at),
    CHECK (execution_epoch >= 0 AND payment_source_version >= 0 AND row_version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE limit_reservation_item
(
    id             VARCHAR(64) NOT NULL,
    tenant_id      VARCHAR(64) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    deleted        TINYINT     NOT NULL DEFAULT 0,
    reservation_id VARCHAR(64) NOT NULL,
    bucket_id      VARCHAR(64) NOT NULL,
    reserved_value BIGINT      NOT NULL,
    consumed_value BIGINT      NOT NULL DEFAULT 0,
    restored_value BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_limit_item_bucket (tenant_id, reservation_id, bucket_id),
    CHECK (reserved_value > 0 AND consumed_value >= 0 AND restored_value >= 0),
    CHECK (consumed_value <= reserved_value AND restored_value <= consumed_value)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

-- Additive execution issuance receipts. No bearer permits or signing keys are stored.
CREATE TABLE limit_execution_grant
(
    id              VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    operation_id    VARCHAR(64)  NOT NULL,
    payment_id      VARCHAR(64)  NOT NULL,
    attempt_id      VARCHAR(64)  NOT NULL,
    execution_epoch BIGINT       NOT NULL,
    request_key     VARCHAR(128) NOT NULL,
    permit_key_id   VARCHAR(64)  NOT NULL,
    permit_hash     CHAR(64)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_limit_grant_epoch (tenant_id, operation_id, execution_epoch),
    UNIQUE KEY uk_limit_grant_request (tenant_id, operation_id, request_key),
    CHECK (execution_epoch > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE limit_refund_restore
(
    id                  VARCHAR(64) NOT NULL,
    tenant_id           VARCHAR(64) NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    deleted             TINYINT     NOT NULL DEFAULT 0,
    refund_id           VARCHAR(64) NOT NULL,
    reservation_item_id VARCHAR(64) NOT NULL,
    source_event_id     VARCHAR(64) NOT NULL,
    restore_value       BIGINT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_limit_refund_restore (tenant_id, refund_id, reservation_item_id),
    CHECK (restore_value >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE limit_operation_history
(
    id              VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    operation_id    VARCHAR(64)  NOT NULL,
    transition_key  VARCHAR(128) NOT NULL,
    transition_type VARCHAR(32)  NOT NULL,
    source_event_id VARCHAR(64)  NULL,
    from_state      VARCHAR(24)  NOT NULL,
    to_state        VARCHAR(24)  NOT NULL,
    facts_digest    CHAR(64)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_limit_history_transition (tenant_id, operation_id, transition_key),
    KEY ix_limit_history (tenant_id, operation_id, created_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
