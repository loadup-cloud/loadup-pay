CREATE TABLE payment_recovery_cursor
(
    id         VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id  VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted    TINYINT     NOT NULL DEFAULT 0,
    generation BIGINT      NOT NULL,
    due_at     DATETIME(6) NOT NULL,
    completed  TINYINT     NOT NULL DEFAULT 0,
    KEY ix_payment_recovery_cursor_tenant (tenant_id),
    CHECK (deleted = 0 AND generation >= 0 AND completed IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
