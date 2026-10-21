-- Acquire-owned A0/A1 intent, order, immutable admission snapshot and one order-total fee.

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
    caller_scope_hash  CHAR(64)    NOT NULL,
    entry_mode         VARCHAR(24) NOT NULL,
    intent_payload     LONGTEXT        NOT NULL,
    state              VARCHAR(24) NOT NULL,
    admission_id       VARCHAR(64) NULL,
    materialize_before DATETIME(6) NULL,
    result_code        VARCHAR(64) NULL,
    row_version        BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_intent_merchant (tenant_id, payee_merchant_id, merchant_order_no),
    UNIQUE KEY uk_intent_order (tenant_id, order_id),
    KEY ix_intent_recovery (tenant_id, state, updated_at),
    CHECK (entry_mode = 'ORDER' AND state IN ('INIT','COMPLETED','REJECTED','EXPIRED')),
    CHECK (row_version >= 0),
    CHECK ((state = 'INIT' AND admission_id IS NULL AND materialize_before IS NULL AND result_code IS NULL)
        OR (state = 'REJECTED' AND admission_id IS NOT NULL AND result_code IS NOT NULL)
        OR (state IN ('COMPLETED','EXPIRED') AND admission_id IS NOT NULL AND materialize_before IS NOT NULL AND result_code IS NOT NULL))
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
    CHECK (amount_minor > 0 AND currency = 'CNY'),
    CHECK (row_version >= 0 AND deadline_version > 0 AND close_epoch >= 0),
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
    configuration       LONGTEXT         NOT NULL,
    payload_hash        CHAR(64)     NOT NULL,
    admission_digest    CHAR(64)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_snapshot_order (tenant_id, order_id),
    CHECK (contract_revision > 0 AND contract_generation > 0 AND schema_version = 1)
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
    fee_rule_snapshot     LONGTEXT        NOT NULL,
    refund_basis_version  BIGINT      NOT NULL DEFAULT 0,
    refund_basis_minor    BIGINT      NOT NULL DEFAULT 0,
    adjusted_amount_minor BIGINT      NOT NULL DEFAULT 0,
    state                 VARCHAR(24) NOT NULL,
    confirmed_at          DATETIME(6) NULL,
    row_version           BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fee_order_type (tenant_id, order_id, fee_type),
    CHECK (base_amount_minor > 0 AND fee_amount_minor >= 0 AND currency = 'CNY'),
    CHECK (fee_type = 'ORDER_AMOUNT' AND state IN ('INIT','CONFIRMED')),
    CHECK (refund_basis_version >= 0 AND row_version >= 0),
    CHECK (refund_basis_minor >= 0 AND refund_basis_minor <= base_amount_minor),
    CHECK (adjusted_amount_minor >= 0 AND adjusted_amount_minor <= fee_amount_minor)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
