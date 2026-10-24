-- DESIGN DRAFT ONLY. Not a Flyway migration. Do not apply to a running database.
-- New pay tables only; framework-owned migrations are intentionally excluded.
-- Session time zone: the deployment system default. No database selection or destructive statements.

CREATE TABLE payment_parent_gate
(
    id                     VARCHAR(64) NOT NULL,
    tenant_id              VARCHAR(64) NOT NULL,
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    deleted                TINYINT     NOT NULL DEFAULT 0,
    order_id               VARCHAR(64) NOT NULL,
    payee_merchant_id      VARCHAR(64) NOT NULL,
    state                  VARCHAR(24) NOT NULL,
    close_epoch            BIGINT      NOT NULL DEFAULT 0,
    refund_summary_version BIGINT      NOT NULL DEFAULT 0,
    refund_summary         JSON        NOT NULL,
    row_version            BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_parent_gate (tenant_id, order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE payment_gate
(
    id          VARCHAR(64) NOT NULL,
    tenant_id   VARCHAR(64) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    order_id    VARCHAR(64) NOT NULL,
    payment_id  VARCHAR(64) NOT NULL,
    state       VARCHAR(24) NOT NULL,
    fence_epoch BIGINT      NOT NULL DEFAULT 0,
    reason_code VARCHAR(64) NULL,
    row_version BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_gate (tenant_id, payment_id),
    KEY ix_payment_gate_order (tenant_id, order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE payment_order
(
    id                    VARCHAR(64) NOT NULL,
    tenant_id             VARCHAR(64) NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    deleted               TINYINT     NOT NULL DEFAULT 0,
    order_id              VARCHAR(64) NOT NULL,
    allocation_id         VARCHAR(64) NOT NULL,
    admission_id          VARCHAR(64) NOT NULL,
    payee_merchant_id     VARCHAR(64) NOT NULL,
    amount_minor          BIGINT      NOT NULL,
    currency              CHAR(3)     NOT NULL,
    request_digest        CHAR(64)    NOT NULL,
    state                 VARCHAR(24) NOT NULL,
    active_attempt_id     VARCHAR(64) NULL,
    successful_attempt_id VARCHAR(64) NULL,
    limit_operation_id    VARCHAR(64) NOT NULL,
    initiated_at          DATETIME(6) NOT NULL,
    succeeded_at          DATETIME(6) NULL,
    terminal_evidence_id  VARCHAR(64) NULL,
    order_terms           JSON        NOT NULL,
    row_version           BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_allocation (tenant_id, allocation_id),
    UNIQUE KEY uk_payment_limit_op (tenant_id, limit_operation_id),
    KEY ix_payment_order (tenant_id, order_id, id),
    KEY ix_payment_payee_page (tenant_id, payee_merchant_id, created_at, id),
    CHECK (amount_minor > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE payment_attempt
(
    id                     VARCHAR(64)  NOT NULL,
    tenant_id              VARCHAR(64)  NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    deleted                TINYINT      NOT NULL DEFAULT 0,
    payment_id             VARCHAR(64)  NOT NULL,
    attempt_no             INT          NOT NULL,
    channel                VARCHAR(24)  NOT NULL,
    access_mode            VARCHAR(24)  NOT NULL,
    payment_method         VARCHAR(32)  NOT NULL,
    binding_revision_id    VARCHAR(64)  NOT NULL,
    account_scope_hash     CHAR(64)     NOT NULL,
    channel_request_no     VARCHAR(64)  NOT NULL,
    channel_transaction_no VARCHAR(128) NULL,
    state                  VARCHAR(24)  NOT NULL,
    input_ref              VARCHAR(64)  NULL,
    execution_epoch        BIGINT       NOT NULL DEFAULT 0,
    execution_permit_hash  CHAR(64)     NULL,
    risk_decision          VARCHAR(24)  NOT NULL,
    risk_reason            VARCHAR(64)  NOT NULL,
    risk_consultation_ref  VARCHAR(128) NULL,
    action_type            VARCHAR(24)  NOT NULL,
    action_payload         JSON         NULL,
    action_expires_at      DATETIME(6)  NULL,
    next_query_at          DATETIME(6)  NULL,
    query_sequence         BIGINT       NOT NULL DEFAULT 0,
    row_version            BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_attempt_no (tenant_id, payment_id, attempt_no),
    UNIQUE KEY uk_attempt_request (channel, account_scope_hash, channel_request_no),
    KEY ix_attempt_callback_request (tenant_id, binding_revision_id, channel_request_no),
    KEY ix_attempt_query (tenant_id, state, next_query_at, id),
    CHECK (attempt_no > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE channel_transaction_claim
(
    id                     VARCHAR(64)  NOT NULL,
    tenant_id              VARCHAR(64)  NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    deleted                TINYINT      NOT NULL DEFAULT 0,
    channel                VARCHAR(24)  NOT NULL,
    account_scope_hash     CHAR(64)     NOT NULL,
    channel_transaction_no VARCHAR(128) NOT NULL,
    payment_id             VARCHAR(64)  NOT NULL,
    attempt_id             VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_txn (channel, account_scope_hash, channel_transaction_no),
    UNIQUE KEY uk_claim_attempt (tenant_id, attempt_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE channel_merchant_binding
(
    id                  VARCHAR(64) NOT NULL,
    tenant_id           VARCHAR(64) NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    deleted             TINYINT     NOT NULL DEFAULT 0,
    payee_merchant_id   VARCHAR(64) NOT NULL,
    binding_code        VARCHAR(64) NOT NULL,
    channel             VARCHAR(24) NOT NULL,
    access_mode         VARCHAR(24) NOT NULL,
    state               VARCHAR(24) NOT NULL,
    current_revision_id VARCHAR(64) NOT NULL,
    row_version         BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_binding_code (tenant_id, payee_merchant_id, binding_code),
    KEY ix_binding_route (tenant_id, payee_merchant_id, channel, access_mode, state)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE channel_binding_revision
(
    id                 VARCHAR(64)  NOT NULL,
    tenant_id          VARCHAR(64)  NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    deleted            TINYINT      NOT NULL DEFAULT 0,
    binding_id         VARCHAR(64)  NOT NULL,
    revision           INT          NOT NULL,
    identity_schema    VARCHAR(32)  NOT NULL,
    identity_payload   JSON         NOT NULL,
    account_scope_hash CHAR(64)     NOT NULL,
    credential_ref     VARCHAR(128) NOT NULL,
    credential_version VARCHAR(64)  NOT NULL,
    capabilities       JSON         NOT NULL,
    identity_hash      CHAR(64)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_binding_revision (tenant_id, binding_id, revision)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE payment_input_secret
(
    id           VARCHAR(64)  NOT NULL,
    tenant_id    VARCHAR(64)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    payment_id   VARCHAR(64)  NOT NULL,
    input_key    VARCHAR(128) NOT NULL,
    input_type   VARCHAR(32)  NOT NULL,
    input_digest CHAR(64)     NOT NULL,
    ciphertext   MEDIUMBLOB   NULL,
    key_ref      VARCHAR(128) NOT NULL,
    expires_at   DATETIME(6)  NOT NULL,
    state        VARCHAR(24)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_input_key (tenant_id, payment_id, input_key),
    KEY ix_input_expiry (tenant_id, state, expires_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE channel_receipt
(
    id                     VARCHAR(64)  NOT NULL,
    tenant_id              VARCHAR(64)  NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    deleted                TINYINT      NOT NULL DEFAULT 0,
    channel                VARCHAR(24)  NOT NULL,
    account_scope_hash     CHAR(64)     NOT NULL,
    receipt_key            VARCHAR(128) NOT NULL,
    receipt_type           VARCHAR(24)  NOT NULL,
    payment_id             VARCHAR(64)  NULL,
    attempt_id             VARCHAR(64)  NULL,
    refund_id              VARCHAR(64)  NULL,
    payload_digest         CHAR(64)     NOT NULL,
    facts_digest           CHAR(64)     NOT NULL,
    verified_facts         JSON         NOT NULL,
    encrypted_evidence_ref VARCHAR(128) NULL,
    received_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_receipt (tenant_id, channel, account_scope_hash, receipt_key),
    KEY ix_receipt_payment (tenant_id, payment_id, received_at, id),
    KEY ix_receipt_refund (tenant_id, refund_id, received_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE payment_anomaly
(
    id             VARCHAR(64)  NOT NULL,
    tenant_id      VARCHAR(64)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    order_id       VARCHAR(64)  NOT NULL,
    payment_id     VARCHAR(64)  NULL,
    receipt_id     VARCHAR(64)  NOT NULL,
    anomaly_type   VARCHAR(64)  NOT NULL,
    state          VARCHAR(24)  NOT NULL,
    resolution_ref VARCHAR(128) NULL,
    row_version    BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_anomaly_receipt_type (tenant_id, receipt_id, anomaly_type),
    KEY ix_anomaly_pending (tenant_id, state, created_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE payment_refund_capacity
(
    id                     VARCHAR(64) NOT NULL,
    tenant_id              VARCHAR(64) NOT NULL,
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    deleted                TINYINT     NOT NULL DEFAULT 0,
    order_id               VARCHAR(64) NOT NULL,
    payment_id             VARCHAR(64) NOT NULL,
    succeeded_at           DATETIME(6) NOT NULL,
    succeeded_amount_minor BIGINT      NOT NULL,
    refunded_minor         BIGINT      NOT NULL DEFAULT 0,
    reserved_minor         BIGINT      NOT NULL DEFAULT 0,
    currency               CHAR(3)     NOT NULL,
    row_version            BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_capacity_payment (tenant_id, payment_id),
    KEY ix_refund_capacity_fifo (tenant_id, order_id, succeeded_at, payment_id),
    CHECK (succeeded_amount_minor > 0 AND refunded_minor >= 0 AND reserved_minor >= 0),
    CHECK (refunded_minor <= succeeded_amount_minor AND reserved_minor <= succeeded_amount_minor - refunded_minor)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE refund_request
(
    id                 VARCHAR(64) NOT NULL,
    tenant_id          VARCHAR(64) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    deleted            TINYINT     NOT NULL DEFAULT 0,
    order_id           VARCHAR(64) NOT NULL,
    payee_merchant_id  VARCHAR(64) NOT NULL,
    merchant_refund_no VARCHAR(64) NOT NULL,
    request_digest     CHAR(64)    NOT NULL,
    amount_minor       BIGINT      NOT NULL,
    currency           CHAR(3)     NOT NULL,
    allocation_policy  VARCHAR(24) NOT NULL,
    plan_version       INT         NOT NULL,
    state              VARCHAR(24) NOT NULL,
    succeeded_minor    BIGINT      NOT NULL DEFAULT 0,
    failed_minor       BIGINT      NOT NULL DEFAULT 0,
    unknown_count      INT         NOT NULL DEFAULT 0,
    refund_terms       JSON        NOT NULL,
    row_version        BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_merchant (tenant_id, payee_merchant_id, merchant_refund_no),
    KEY ix_refund_request_order (tenant_id, order_id, created_at, id),
    CHECK (amount_minor > 0 AND succeeded_minor >= 0 AND failed_minor >= 0),
    CHECK (succeeded_minor <= amount_minor AND failed_minor <= amount_minor - succeeded_minor)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE refund_allocation_plan
(
    id                VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted           TINYINT     NOT NULL DEFAULT 0,
    refund_request_id VARCHAR(64) NOT NULL,
    plan_version      INT         NOT NULL,
    sequence_no       INT         NOT NULL,
    payment_id        VARCHAR(64) NOT NULL,
    refund_id         VARCHAR(64) NOT NULL,
    amount_minor      BIGINT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_plan_seq (tenant_id, refund_request_id, plan_version, sequence_no),
    UNIQUE KEY uk_refund_plan_payment (tenant_id, refund_request_id, plan_version, payment_id),
    UNIQUE KEY uk_refund_plan_child (tenant_id, refund_id),
    CHECK (amount_minor > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE refund_order
(
    id                            VARCHAR(64)  NOT NULL,
    tenant_id                     VARCHAR(64)  NOT NULL,
    created_at                    DATETIME(6)  NOT NULL,
    updated_at                    DATETIME(6)  NOT NULL,
    deleted                       TINYINT      NOT NULL DEFAULT 0,
    refund_request_id             VARCHAR(64)  NOT NULL,
    payment_id                    VARCHAR(64)  NOT NULL,
    original_attempt_id           VARCHAR(64)  NOT NULL,
    binding_revision_id           VARCHAR(64)  NOT NULL,
    channel                       VARCHAR(24)  NOT NULL,
    account_scope_hash            CHAR(64)     NOT NULL,
    channel_refund_no             VARCHAR(64)  NOT NULL,
    channel_refund_transaction_no VARCHAR(128) NULL,
    amount_minor                  BIGINT       NOT NULL,
    currency                      CHAR(3)      NOT NULL,
    state                         VARCHAR(24)  NOT NULL,
    terminal_evidence_id          VARCHAR(64)  NULL,
    next_query_at                 DATETIME(6)  NULL,
    query_sequence                BIGINT       NOT NULL DEFAULT 0,
    succeeded_at                  DATETIME(6)  NULL,
    row_version                   BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_channel_no (channel, account_scope_hash, channel_refund_no),
    KEY ix_refund_child_request (tenant_id, refund_request_id, id),
    KEY ix_refund_callback_lookup (tenant_id, binding_revision_id, channel_refund_no),
    KEY ix_refund_query (tenant_id, state, next_query_at, id),
    CHECK (amount_minor > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

-- Merchant notifications are registered and dispatched by acquire; payment owns the refund Outbox source.


CREATE TABLE refund_recovery_cursor (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    generation BIGINT NOT NULL,
    due_at DATETIME(6) NOT NULL,
    completed TINYINT NOT NULL DEFAULT 0,
    KEY ix_refund_recovery_cursor_tenant (tenant_id),
    CHECK (deleted = 0 AND generation >= 0 AND completed IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE payment_action (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    payment_id VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    binding_revision_id VARCHAR(64) NOT NULL,
    account_scope_hash VARCHAR(64) NOT NULL,
    channel_request_no VARCHAR(64) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    execution_epoch BIGINT NOT NULL,
    receipt_id VARCHAR(64) NOT NULL,
    source_digest VARCHAR(64) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    action_kind VARCHAR(32) NOT NULL,
    action_payload LONGTEXT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    KEY ix_payment_action_owner (tenant_id, payment_id),
    UNIQUE KEY uk_payment_action_receipt (tenant_id, receipt_id),
    CHECK (deleted = 0 AND amount_minor > 0 AND execution_epoch > 0 AND currency = 'CNY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;


CREATE TABLE payment_recovery_cursor (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    generation BIGINT NOT NULL,
    due_at DATETIME(6) NOT NULL,
    completed TINYINT NOT NULL DEFAULT 0,
    KEY ix_payment_recovery_cursor_tenant (tenant_id),
    CHECK (deleted = 0 AND generation >= 0 AND completed IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
