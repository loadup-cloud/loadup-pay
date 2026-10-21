-- Additive execution issuance receipts. No bearer permits or signing keys are stored.
CREATE TABLE limit_execution_grant
(
    id              VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    operation_id    VARCHAR(64)  NOT NULL,
    payment_id      VARCHAR(64)  NOT NULL,
    attempt_id      VARCHAR(64)  NOT NULL,
    execution_epoch BIGINT       NOT NULL,
    request_key     VARCHAR(128) NOT NULL,
    permit_key_id   VARCHAR(64)  NOT NULL,
    permit_hash     CHAR(64)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_limit_grant_epoch (tenant_id, operation_id, execution_epoch),
    UNIQUE KEY uk_limit_grant_request (tenant_id, operation_id, request_key),
    CHECK (execution_epoch > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
