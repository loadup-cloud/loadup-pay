CREATE TABLE fee_refund_receipt
(
    id                VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id         VARCHAR(64) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted           TINYINT     NOT NULL DEFAULT 0,
    delivery_id       VARCHAR(64) NOT NULL,
    business_event_id VARCHAR(64) NOT NULL,
    order_id          VARCHAR(64) NOT NULL,
    source_version    BIGINT      NOT NULL,
    applied_version   BIGINT      NOT NULL,
    request_digest    CHAR(64)    NOT NULL,
    caller_service_id VARCHAR(64) NOT NULL,
    accepted_at       DATETIME(6) NOT NULL,
    UNIQUE KEY uk_fee_refund_delivery (tenant_id, delivery_id),
    KEY ix_fee_refund_order (tenant_id, order_id, applied_version),
    CHECK (deleted = 0 AND source_version > 0 AND applied_version >= source_version)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
