CREATE TABLE order_refund_summary
(
    id                 VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id          VARCHAR(64) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    deleted            TINYINT     NOT NULL DEFAULT 0,
    order_id           VARCHAR(64) NOT NULL,
    payee_merchant_id  VARCHAR(64) NOT NULL,
    order_amount_minor BIGINT      NOT NULL,
    currency           CHAR(3)     NOT NULL,
    refunded_minor     BIGINT      NOT NULL DEFAULT 0,
    reserved_minor     BIGINT      NOT NULL DEFAULT 0,
    row_version        BIGINT      NOT NULL,
    UNIQUE KEY uk_order_refund_summary (tenant_id, order_id),
    CHECK (deleted = 0 AND order_amount_minor > 0 AND currency = 'CNY' AND row_version > 0),
    CHECK (refunded_minor >= 0 AND reserved_minor >= 0 AND refunded_minor <= order_amount_minor
        AND reserved_minor <= order_amount_minor - refunded_minor)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
