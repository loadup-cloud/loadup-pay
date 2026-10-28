-- Metadata queries are merchant scoped; wake decisions and native scheduling share the acquire transaction.
ALTER TABLE merchant_notification_delivery
    ADD KEY ix_notification_merchant (tenant_id, payee_merchant_id, occurred_at, id);

CREATE TABLE merchant_notification_wake_receipt
(
    id                VARCHAR(64)  NOT NULL,
    tenant_id         VARCHAR(64)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    delivery_id       VARCHAR(64)  NOT NULL,
    event_id          VARCHAR(64)  NOT NULL,
    request_key       VARCHAR(128) NOT NULL,
    caller_scope_hash CHAR(64)     NOT NULL,
    principal_type    VARCHAR(24)  NOT NULL,
    principal_id      VARCHAR(128) NOT NULL,
    reason            VARCHAR(500) NOT NULL,
    request_digest    CHAR(64)     NOT NULL,
    result            VARCHAR(24)  NOT NULL,
    attempt_count     INT          NOT NULL,
    delivery_version  BIGINT       NOT NULL,
    native_task_id    VARCHAR(64)  NULL,
    accepted_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_wake_request (tenant_id, caller_scope_hash, request_key),
    KEY ix_notification_wake_delivery (tenant_id, delivery_id, accepted_at, id),
    CHECK (principal_type IN ('OPERATOR', 'SERVICE')),
    CHECK (attempt_count >= 0 AND delivery_version >= 0),
    CHECK (result IN
           ('REGISTERED', 'RETRIED', 'RESCHEDULED', 'PENDING', 'RUNNING', 'WAITING', 'ACKED', 'EXHAUSTED', 'EXPIRED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
