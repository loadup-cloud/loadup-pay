-- First verified provider refund acceptance time, independent of financial aggregate versions.
CREATE TABLE refund_acceptance_evidence
(
    id                              VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_id                       VARCHAR(64)  NOT NULL,
    created_at                      DATETIME(6)  NOT NULL,
    updated_at                      DATETIME(6)  NOT NULL,
    deleted                         TINYINT      NOT NULL DEFAULT 0,
    payment_id                      VARCHAR(64)  NOT NULL,
    attempt_id                      VARCHAR(64)  NOT NULL,
    channel                         VARCHAR(32)  NOT NULL,
    binding_revision_id             VARCHAR(64)  NOT NULL,
    account_scope_hash              VARCHAR(64)  NOT NULL,
    channel_request_no              VARCHAR(64)  NOT NULL,
    original_channel_transaction_no VARCHAR(128) NOT NULL,
    channel_refund_no               VARCHAR(64)  NOT NULL,
    amount_minor                    BIGINT       NOT NULL,
    currency                        VARCHAR(3)   NOT NULL,
    accepted_at                     DATETIME(6)  NOT NULL,
    receipt_id                      VARCHAR(64)  NOT NULL,
    CHECK (deleted = 0 AND amount_minor > 0 AND currency = 'CNY')
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
