-- Payment-owned durable acceptance. All business timestamps use the deployment system local time.
-- No channel attempts, raw sensitive inputs, refunds or external submissions are created here.
CREATE TABLE payment_parent_gate
(
    id                VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted           TINYINT     NOT NULL DEFAULT 0,
    order_id          VARCHAR(64) NOT NULL,
    payee_merchant_id VARCHAR(64) NOT NULL,
    caller_service_id VARCHAR(64) NOT NULL,
    state             VARCHAR(24) NOT NULL,
    close_epoch       BIGINT      NOT NULL DEFAULT 0,
    row_version       BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_parent_gate (tenant_id, order_id),
    CHECK (state IN ('INIT', 'OPEN', 'FENCED')),
    CHECK ((state = 'FENCED' AND close_epoch > 0) OR (state <> 'FENCED' AND close_epoch = 0)),
    CHECK (row_version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE payment_gate
(
    id                VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted           TINYINT     NOT NULL DEFAULT 0,
    order_id          VARCHAR(64) NOT NULL,
    payment_id        VARCHAR(64) NOT NULL,
    payee_merchant_id VARCHAR(64) NOT NULL,
    caller_service_id VARCHAR(64) NOT NULL,
    state             VARCHAR(24) NOT NULL,
    fence_epoch       BIGINT      NOT NULL DEFAULT 0,
    reason_code       VARCHAR(64) NULL,
    row_version       BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_gate (tenant_id, payment_id),
    KEY ix_payment_gate_order (tenant_id, order_id),
    CHECK (state IN ('INIT', 'OPEN', 'FENCED')),
    CHECK ((state = 'FENCED' AND fence_epoch > 0) OR (state <> 'FENCED' AND fence_epoch = 0)),
    CHECK (row_version >= 0)
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
    caller_service_id     VARCHAR(64) NOT NULL,
    state                 VARCHAR(24) NOT NULL,
    active_attempt_id     VARCHAR(64) NULL,
    successful_attempt_id VARCHAR(64) NULL,
    limit_operation_id    VARCHAR(64) NOT NULL,
    initiated_at          DATETIME(6) NOT NULL,
    succeeded_at          DATETIME(6) NULL,
    terminal_evidence_id  VARCHAR(64) NULL,
    intent_payload        LONGTEXT    NOT NULL,
    payload_hash          CHAR(64)    NOT NULL,
    row_version           BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_allocation (tenant_id, allocation_id),
    UNIQUE KEY uk_payment_limit_op (tenant_id, limit_operation_id),
    KEY ix_payment_order (tenant_id, order_id, id),
    KEY ix_payment_payee_page (tenant_id, payee_merchant_id, created_at, id),
    CHECK (amount_minor > 0 AND currency = 'CNY'),
    CHECK (state IN ('INIT', 'PROCESSING', 'UNKNOWN', 'CLOSING', 'SUCCEEDED', 'FAILED', 'CLOSED')),
    CHECK ((state = 'SUCCEEDED' AND successful_attempt_id IS NOT NULL AND active_attempt_id IS NOT NULL AND
            active_attempt_id = successful_attempt_id)
        OR (state <> 'SUCCEEDED' AND successful_attempt_id IS NULL)),
    CHECK (state <> 'INIT' OR (active_attempt_id IS NULL AND successful_attempt_id IS NULL)),
    CHECK (row_version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
