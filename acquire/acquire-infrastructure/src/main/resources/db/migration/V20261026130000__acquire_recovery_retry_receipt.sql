CREATE TABLE acquire_recovery_retry_receipt
(
    id                       VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_id                VARCHAR(64)  NOT NULL,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    deleted                  TINYINT      NOT NULL DEFAULT 0,
    request_key              VARCHAR(128) NOT NULL,
    caller_scope_hash        CHAR(64)     NOT NULL,
    caller_service_id        VARCHAR(64)  NOT NULL,
    request_digest           CHAR(64)     NOT NULL,
    kind                     VARCHAR(32)  NOT NULL,
    subject_id               VARCHAR(64)  NOT NULL,
    generation               BIGINT       NOT NULL,
    order_id                 VARCHAR(64)  NOT NULL,
    native_task_id           VARCHAR(64)  NOT NULL,
    expected_attempts        INT          NOT NULL,
    expected_next_execute_at DATETIME(6)  NOT NULL,
    reason                   VARCHAR(500) NOT NULL,
    result                   VARCHAR(32)  NOT NULL,
    accepted_at              DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_acquire_recovery_retry (tenant_id, caller_scope_hash, request_key),
    KEY ix_acquire_recovery_retry_order (tenant_id, order_id, accepted_at),
    CHECK (deleted = 0 AND generation >= 0 AND expected_attempts > 0),
    CHECK (kind IN ('PAYMENT_ALLOCATION', 'ORDER_DEADLINE', 'ORDER_CLOSURE')),
    CHECK (kind <> 'ORDER_DEADLINE' OR generation > 0),
    CHECK (result IN ('RETRIED', 'NO_LONGER_NEEDED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
