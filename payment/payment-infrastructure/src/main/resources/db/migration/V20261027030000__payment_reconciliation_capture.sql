-- Immutable source-owned reconciliation captures. Readiness never comes from a caller's completeness assertion.
CREATE TABLE payment_reconciliation_capture
(
    id                   VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_id            VARCHAR(64)  NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    deleted              TINYINT      NOT NULL DEFAULT 0,
    caller_service_id    VARCHAR(64)  NOT NULL,
    request_key          VARCHAR(128) NOT NULL,
    request_digest       VARCHAR(64)  NOT NULL,
    channel              VARCHAR(32)  NOT NULL,
    account_scope_hash   VARCHAR(64)  NOT NULL,
    bill_date            DATE         NOT NULL,
    statement_profile    VARCHAR(64)  NOT NULL,
    system_zone          VARCHAR(64)  NOT NULL,
    state                VARCHAR(16)  NOT NULL,
    started_at           DATETIME(6)  NULL,
    finished_at          DATETIME(6)  NULL,
    payment_count        BIGINT       NULL,
    payment_amount_minor BIGINT       NULL,
    refund_count         BIGINT       NULL,
    refund_amount_minor  BIGINT       NULL,
    content_hash         VARCHAR(64)  NULL,
    blocker              VARCHAR(64)  NULL,
    blocker_subject_id   VARCHAR(64)  NULL,
    row_version          BIGINT       NOT NULL,
    UNIQUE KEY uk_payment_capture_request (tenant_id, caller_service_id, request_key),
    CHECK (deleted = 0 AND channel = 'WECHAT' AND statement_profile = 'wechat-trade-all-20260116' AND
           row_version = CASE WHEN state = 'INIT' THEN 0 ELSE 1 END),
    CHECK ((state = 'INIT' AND started_at IS NULL AND finished_at IS NULL)
        OR (state IN ('SEALED', 'BLOCKED') AND started_at IS NOT NULL AND finished_at IS NOT NULL AND
            finished_at >= started_at)),
    CHECK ((state = 'SEALED' AND payment_count IS NOT NULL AND payment_amount_minor IS NOT NULL AND
            refund_count IS NOT NULL
        AND refund_amount_minor IS NOT NULL AND content_hash IS NOT NULL AND payment_count >= 0 AND refund_count >= 0
        AND payment_amount_minor >= payment_count AND refund_amount_minor >= refund_count
        AND ((payment_count = 0 AND payment_amount_minor = 0) OR (payment_count > 0 AND payment_amount_minor > 0))
        AND ((refund_count = 0 AND refund_amount_minor = 0) OR (refund_count > 0 AND refund_amount_minor > 0)))
        OR (state IN ('INIT', 'BLOCKED') AND payment_count IS NULL AND payment_amount_minor IS NULL AND
            refund_count IS NULL AND refund_amount_minor IS NULL AND content_hash IS NULL)),
    CHECK ((state = 'BLOCKED' AND blocker IS NOT NULL AND blocker_subject_id IS NOT NULL)
        OR (state IN ('INIT', 'SEALED') AND blocker IS NULL AND blocker_subject_id IS NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE payment_reconciliation_line
(
    id                     VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_id              VARCHAR(64)  NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    deleted                TINYINT      NOT NULL DEFAULT 0,
    capture_id             VARCHAR(64)  NOT NULL,
    ordinal                BIGINT       NOT NULL,
    key_hash               VARCHAR(64)  NOT NULL,
    kind                   VARCHAR(16)  NOT NULL,
    channel_transaction_no VARCHAR(128) NOT NULL,
    channel_refund_no      VARCHAR(64)  NULL,
    merchant_payment_no    VARCHAR(64)  NOT NULL,
    amount_minor           BIGINT       NOT NULL,
    currency               VARCHAR(3)   NOT NULL,
    status                 VARCHAR(16)  NOT NULL,
    source_at              DATETIME(6)  NOT NULL,
    source_id              VARCHAR(64)  NOT NULL,
    evidence_id            VARCHAR(64)  NOT NULL,
    source_version         BIGINT       NOT NULL,
    UNIQUE KEY uk_payment_capture_ordinal (tenant_id, capture_id, ordinal),
    UNIQUE KEY uk_payment_capture_key (tenant_id, capture_id, key_hash),
    CHECK (deleted = 0 AND ordinal > 0 AND amount_minor > 0 AND currency = 'CNY' AND source_version >= 0),
    CHECK ((kind = 'PAYMENT' AND channel_refund_no IS NULL AND status = 'SUCCEEDED')
        OR (kind = 'REFUND' AND channel_refund_no IS NOT NULL AND
            status IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'UNKNOWN')))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

-- This owned evidence index directly supports the implemented channel/account/date range query.
CREATE INDEX ix_refund_acceptance_window ON refund_acceptance_evidence (tenant_id, channel, account_scope_hash, accepted_at, id);
