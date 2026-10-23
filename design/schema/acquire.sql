-- DESIGN DRAFT ONLY. Not a Flyway migration. Do not apply to a running database.
-- New pay tables only; framework-owned migrations are intentionally excluded.
-- Session time zone: the deployment system default. No database selection or destructive statements.

CREATE TABLE order_create_intent
(
    id                 VARCHAR(64) NOT NULL,
    tenant_id          VARCHAR(64) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    deleted            TINYINT     NOT NULL DEFAULT 0,
    payee_merchant_id  VARCHAR(64) NOT NULL,
    merchant_order_no  VARCHAR(64) NOT NULL,
    order_id           VARCHAR(64) NOT NULL,
    request_digest     CHAR(64)    NOT NULL,
    entry_mode         VARCHAR(24) NOT NULL,
    intent_payload     JSON        NOT NULL,
    state              VARCHAR(24) NOT NULL,
    admission_id       VARCHAR(64) NULL,
    materialize_before DATETIME(6) NULL,
    result_code        VARCHAR(64) NULL,
    row_version        BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_intent_merchant (tenant_id, payee_merchant_id, merchant_order_no),
    UNIQUE KEY uk_intent_order (tenant_id, order_id),
    KEY ix_intent_recovery (tenant_id, state, updated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE acquire_order
(
    id                       VARCHAR(64)  NOT NULL,
    tenant_id                VARCHAR(64)  NOT NULL,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    deleted                  TINYINT      NOT NULL DEFAULT 0,
    payee_merchant_id        VARCHAR(64)  NOT NULL,
    merchant_order_no        VARCHAR(64)  NOT NULL,
    admission_id             VARCHAR(64)  NOT NULL,
    contract_snapshot_id     VARCHAR(64)  NOT NULL,
    subject                  VARCHAR(200) NOT NULL,
    currency                 CHAR(3)      NOT NULL,
    amount_minor             BIGINT       NOT NULL,
    paid_amount_minor        BIGINT       NOT NULL DEFAULT 0,
    payment_reserved_minor   BIGINT       NOT NULL DEFAULT 0,
    successful_payment_count INT          NOT NULL DEFAULT 0,
    reserved_payment_count   INT          NOT NULL DEFAULT 0,
    state                    VARCHAR(24)  NOT NULL,
    admitted_at              DATETIME(6)  NOT NULL,
    order_due_at             DATETIME(6)  NOT NULL,
    deadline_version         BIGINT       NOT NULL DEFAULT 1,
    close_epoch              BIGINT       NOT NULL DEFAULT 0,
    paid_at                  DATETIME(6)  NULL,
    closed_at                DATETIME(6)  NULL,
    row_version              BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_merchant (tenant_id, payee_merchant_id, merchant_order_no),
    UNIQUE KEY uk_order_admission (tenant_id, admission_id),
    KEY ix_order_payee_page (tenant_id, payee_merchant_id, created_at, id),
    KEY ix_order_due (tenant_id, state, order_due_at, id),
    CHECK (amount_minor > 0),
    CHECK (paid_amount_minor >= 0 AND payment_reserved_minor >= 0),
    CHECK (paid_amount_minor <= amount_minor AND payment_reserved_minor <= amount_minor - paid_amount_minor),
    CHECK (successful_payment_count >= 0 AND reserved_payment_count >= 0),
    CHECK (state IN ('INIT', 'PENDING', 'CLOSING', 'PAID', 'CLOSED')),
    CHECK (state <> 'PAID' OR (paid_amount_minor = amount_minor AND payment_reserved_minor = 0))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE order_contract_snapshot
(
    id                  VARCHAR(64)  NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    order_id            VARCHAR(64)  NOT NULL,
    contract_id         VARCHAR(64)  NOT NULL,
    contract_revision   INT          NOT NULL,
    contract_generation BIGINT       NOT NULL,
    snapshot_hash       VARCHAR(128) NOT NULL,
    compiled_terms_hash CHAR(64)     NOT NULL,
    schema_version      INT          NOT NULL,
    configuration       JSON         NOT NULL,
    admission_digest    CHAR(64)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_snapshot_order (tenant_id, order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

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
    amount_minor           BIGINT       NOT NULL,
    currency               CHAR(3)      NOT NULL,
    state                  VARCHAR(24)  NOT NULL,
    execution_intent       JSON         NOT NULL,
    input_state            VARCHAR(24)  NOT NULL,
    payment_source_version BIGINT       NOT NULL DEFAULT 0,
    last_business_event_id VARCHAR(64)  NULL,
    consumed_at            DATETIME(6)  NULL,
    released_at            DATETIME(6)  NULL,
    row_version            BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_allocation_payment (tenant_id, payment_id),
    UNIQUE KEY uk_allocation_action (tenant_id, order_id, actor_scope, action_key),
    KEY ix_allocation_order (tenant_id, order_id, state, payment_id),
    CHECK (amount_minor > 0),
    CHECK (state IN ('INIT', 'HELD', 'CONSUMED', 'RELEASED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE order_fee
(
    id                    VARCHAR(64) NOT NULL,
    tenant_id             VARCHAR(64) NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    deleted               TINYINT     NOT NULL DEFAULT 0,
    order_id              VARCHAR(64) NOT NULL,
    fee_type              VARCHAR(24) NOT NULL,
    currency              CHAR(3)     NOT NULL,
    base_amount_minor     BIGINT      NOT NULL,
    fee_amount_minor      BIGINT      NOT NULL,
    fee_rule_snapshot     JSON        NOT NULL,
    refund_basis_version  BIGINT      NOT NULL DEFAULT 0,
    refund_basis_minor    BIGINT      NOT NULL DEFAULT 0,
    adjusted_amount_minor BIGINT      NOT NULL DEFAULT 0,
    state                 VARCHAR(24) NOT NULL,
    confirmed_at          DATETIME(6) NULL,
    row_version           BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fee_order_type (tenant_id, order_id, fee_type),
    CHECK (base_amount_minor > 0 AND fee_amount_minor >= 0),
    CHECK (refund_basis_minor >= 0 AND refund_basis_minor <= base_amount_minor),
    CHECK (adjusted_amount_minor >= 0 AND adjusted_amount_minor <= fee_amount_minor)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE fee_adjustment (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    order_id VARCHAR(64) NOT NULL,
    fee_id VARCHAR(64) NOT NULL,
    trigger_key VARCHAR(128) NOT NULL,
    source_event_id VARCHAR(64) NOT NULL,
    source_version BIGINT NOT NULL,
    adjustment_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    cumulative_refunded_minor BIGINT NOT NULL,
    rule_snapshot LONGTEXT NOT NULL,
    applied_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_fee_adjust_trigger (tenant_id, fee_id, trigger_key),
    KEY ix_fee_adjust_order (tenant_id, order_id, created_at, id),
    CHECK (deleted = 0 AND adjustment_minor > 0 AND currency = 'CNY' AND source_version > 0 AND cumulative_refunded_minor > 0),
    CHECK (JSON_VALID(rule_snapshot) AND OCTET_LENGTH(rule_snapshot) <= 32768)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE fee_refund_receipt (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    delivery_id VARCHAR(64) NOT NULL,
    business_event_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    source_version BIGINT NOT NULL,
    applied_version BIGINT NOT NULL,
    request_digest CHAR(64) NOT NULL,
    caller_service_id VARCHAR(64) NOT NULL,
    accepted_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_fee_refund_delivery (tenant_id, delivery_id),
    KEY ix_fee_refund_order (tenant_id, order_id, applied_version),
    CHECK (deleted = 0 AND source_version > 0 AND applied_version >= source_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

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
    scope_snapshot    JSON         NOT NULL,
    row_version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_request (tenant_id, order_id, request_key),
    UNIQUE KEY uk_session_token (token_hash),
    KEY ix_session_order (tenant_id, order_id, state)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE order_command_receipt
(
    id             VARCHAR(64)  NOT NULL,
    tenant_id      VARCHAR(64)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    order_id       VARCHAR(64)  NOT NULL,
    command_type   VARCHAR(32)  NOT NULL,
    request_key    VARCHAR(128) NOT NULL,
    request_digest CHAR(64)     NOT NULL,
    result_payload JSON         NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_command (tenant_id, order_id, command_type, request_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE merchant_notification_delivery
(
    id                VARCHAR(64)  NOT NULL,
    tenant_id         VARCHAR(64)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    business_event_id VARCHAR(64)  NOT NULL,
    aggregate_id      VARCHAR(64)  NOT NULL,
    aggregate_version BIGINT       NOT NULL,
    event_type        VARCHAR(64)  NOT NULL,
    payee_merchant_id VARCHAR(64)  NOT NULL,
    target_ref        VARCHAR(128) NOT NULL,
    key_ref           VARCHAR(128) NOT NULL,
    body_bytes        MEDIUMBLOB   NOT NULL,
    body_digest       CHAR(64)     NOT NULL,
    policy_snapshot   JSON         NOT NULL,
    state             VARCHAR(24)  NOT NULL,
    attempt_count     INT          NOT NULL DEFAULT 0,
    next_attempt_at   DATETIME(6)  NULL,
    acknowledged_at   DATETIME(6)  NULL,
    row_version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_event_target (tenant_id, business_event_id, target_ref),
    KEY ix_notification_pending (tenant_id, state, next_attempt_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
