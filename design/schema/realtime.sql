-- DESIGN DRAFT ONLY. Not a Flyway migration. Do not apply to a running database.
-- New pay tables only; framework-owned migrations are intentionally excluded.
-- Session time zone: the deployment system default. No database selection or destructive statements.

CREATE TABLE trade_order_projection
(
    id                     VARCHAR(64) NOT NULL,
    tenant_id              VARCHAR(64) NOT NULL,
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    deleted                TINYINT     NOT NULL DEFAULT 0,
    order_id               VARCHAR(64) NOT NULL,
    payee_merchant_id      VARCHAR(64) NOT NULL,
    merchant_order_no      VARCHAR(64) NOT NULL,
    currency               CHAR(3)     NOT NULL,
    amount_minor           BIGINT      NOT NULL,
    paid_amount_minor      BIGINT      NOT NULL,
    payment_reserved_minor BIGINT      NOT NULL,
    state                  VARCHAR(24) NOT NULL,
    source_version         BIGINT      NOT NULL,
    source_updated_at      DATETIME(6) NOT NULL,
    order_created_at       DATETIME(6) NOT NULL,
    snapshot_at            DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_order (tenant_id, order_id),
    UNIQUE KEY uk_trade_merchant_order (tenant_id, payee_merchant_id, merchant_order_no),
    KEY ix_trade_order_page (tenant_id, payee_merchant_id, order_created_at, order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE trade_payment_projection
(
    id                VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted           TINYINT     NOT NULL DEFAULT 0,
    payment_id        VARCHAR(64) NOT NULL,
    order_id          VARCHAR(64) NOT NULL,
    payee_merchant_id VARCHAR(64) NOT NULL,
    amount_minor      BIGINT      NOT NULL,
    currency          CHAR(3)     NOT NULL,
    state             VARCHAR(24) NOT NULL,
    channel           VARCHAR(24) NULL,
    access_mode       VARCHAR(24) NULL,
    succeeded_at      DATETIME(6) NULL,
    source_version    BIGINT      NOT NULL,
    snapshot_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_payment (tenant_id, payment_id),
    KEY ix_trade_payment_order (tenant_id, order_id, payment_id),
    KEY ix_trade_payment_page (tenant_id, payee_merchant_id, created_at, payment_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE trade_refund_projection
(
    id                VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted           TINYINT     NOT NULL DEFAULT 0,
    refund_id         VARCHAR(64) NOT NULL,
    refund_request_id VARCHAR(64) NOT NULL,
    payment_id        VARCHAR(64) NOT NULL,
    order_id          VARCHAR(64) NOT NULL,
    payee_merchant_id VARCHAR(64) NOT NULL,
    amount_minor      BIGINT      NOT NULL,
    currency          CHAR(3)     NOT NULL,
    state             VARCHAR(24) NOT NULL,
    succeeded_at      DATETIME(6) NULL,
    source_version    BIGINT      NOT NULL,
    snapshot_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_refund (tenant_id, refund_id),
    KEY ix_trade_refund_order (tenant_id, order_id, refund_id),
    KEY ix_trade_refund_request (tenant_id, refund_request_id, refund_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE trade_refund_summary
(
    id                    VARCHAR(64) NOT NULL,
    tenant_id             VARCHAR(64) NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    deleted               TINYINT     NOT NULL DEFAULT 0,
    order_id              VARCHAR(64) NOT NULL,
    refunded_minor        BIGINT      NOT NULL,
    refund_reserved_minor BIGINT      NOT NULL,
    request_summary       JSON        NOT NULL,
    source_version        BIGINT      NOT NULL,
    snapshot_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_refund_summary (tenant_id, order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE projection_checkpoint
(
    id              VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    producer        VARCHAR(32)  NOT NULL,
    stream_key      VARCHAR(128) NOT NULL,
    snapshot_cursor VARCHAR(512) NULL,
    snapshot_cutoff DATETIME(6)  NULL,
    state           VARCHAR(24)  NOT NULL,
    row_version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_projection_checkpoint (tenant_id, producer, stream_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
