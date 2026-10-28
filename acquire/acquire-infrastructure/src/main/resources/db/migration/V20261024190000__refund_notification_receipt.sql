-- Durable internal registration acknowledgement; merchant delivery uses merchant_notification_delivery.
CREATE TABLE refund_notification_receipt
(
    id                VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted           TINYINT     NOT NULL DEFAULT 0,
    delivery_id       VARCHAR(64) NOT NULL,
    business_event_id VARCHAR(64) NOT NULL,
    aggregate_id      VARCHAR(64) NOT NULL,
    source_version    BIGINT      NOT NULL,
    order_id          VARCHAR(64) NOT NULL,
    payee_merchant_id VARCHAR(64) NOT NULL,
    request_digest    CHAR(64)    NOT NULL,
    caller_service_id VARCHAR(64) NOT NULL,
    merchant_event_id CHAR(64)    NOT NULL,
    disposition       VARCHAR(24) NOT NULL,
    accepted_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_notification_delivery (tenant_id, delivery_id),
    KEY ix_refund_notification_order (tenant_id, order_id, accepted_at, id),
    CHECK (source_version > 0),
    CHECK (disposition IN ('ENQUEUED', 'DISABLED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
