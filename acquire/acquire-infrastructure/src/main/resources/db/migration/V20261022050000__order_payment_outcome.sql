-- A terminal fact is inserted in the same transaction as the original hold and order update.
CREATE TABLE order_payment_outcome
(
    id                     VARCHAR(64) NOT NULL,
    tenant_id              VARCHAR(64) NOT NULL,
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    deleted                TINYINT     NOT NULL DEFAULT 0,
    allocation_id          VARCHAR(64) NOT NULL,
    order_id               VARCHAR(64) NOT NULL,
    payment_id             VARCHAR(64) NOT NULL,
    amount_minor           BIGINT      NOT NULL,
    currency               CHAR(3)     NOT NULL,
    outcome_kind           VARCHAR(24) NOT NULL,
    payment_source_version BIGINT      NOT NULL,
    evidence_id            VARCHAR(64) NOT NULL,
    execution_fenced       TINYINT     NOT NULL,
    observed_at            DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_outcome_allocation (tenant_id, allocation_id),
    UNIQUE KEY uk_order_outcome_payment (tenant_id, payment_id),
    KEY ix_order_outcome_order (tenant_id, order_id, payment_id),
    CHECK (amount_minor > 0 AND currency = 'CNY'),
    CONSTRAINT ck_order_outcome_source CHECK (
        (outcome_kind = 'NOT_ACCEPTED' AND payment_source_version = 0)
            OR (outcome_kind IN ('SUCCEEDED', 'NO_COLLECTION') AND payment_source_version > 0)),
    CONSTRAINT ck_order_outcome_fence CHECK (
        execution_fenced IN (0, 1) AND (outcome_kind = 'SUCCEEDED' OR execution_fenced = 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
