-- Refund planning is independent of acquiring order/payment lifecycle and gross collection amounts.
CREATE TABLE refund_request
(
    id                  VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    deleted             TINYINT     NOT NULL DEFAULT 0,
    order_id            VARCHAR(64) NOT NULL,
    payee_merchant_id   VARCHAR(64) NOT NULL,
    merchant_refund_no  VARCHAR(64) NOT NULL,
    reason_code         VARCHAR(64) NOT NULL,
    request_digest      CHAR(64)    NOT NULL,
    caller_subject_hash CHAR(64)    NOT NULL,
    admission_id        VARCHAR(64) NOT NULL,
    amount_minor        BIGINT      NOT NULL,
    currency            CHAR(3)     NOT NULL,
    allocation_policy   VARCHAR(24) NOT NULL,
    plan_version        INT         NOT NULL,
    state               VARCHAR(24) NOT NULL,
    succeeded_minor     BIGINT      NOT NULL DEFAULT 0,
    failed_minor        BIGINT      NOT NULL DEFAULT 0,
    unknown_count       INT         NOT NULL DEFAULT 0,
    refund_terms        LONGTEXT    NOT NULL,
    terms_hash          CHAR(64)    NOT NULL,
    planned_at          DATETIME(6) NOT NULL,
    row_version         BIGINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_refund_merchant (tenant_id, payee_merchant_id, merchant_refund_no),
    KEY ix_refund_request_order (tenant_id, order_id, created_at, id),
    CHECK (deleted = 0 AND amount_minor > 0 AND currency = 'CNY' AND plan_version = 1 AND row_version >= 0),
    CHECK (allocation_policy = 'FIFO' AND JSON_VALID(refund_terms) AND OCTET_LENGTH(refund_terms) <= 262144),
    CHECK (state IN ('INIT', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'PARTIAL_SUCCEEDED')),
    CHECK (succeeded_minor >= 0 AND failed_minor >= 0 AND unknown_count >= 0
        AND succeeded_minor <= amount_minor AND failed_minor <= amount_minor - succeeded_minor)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE refund_allocation_plan
(
    id                VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id         VARCHAR(64) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted           TINYINT     NOT NULL DEFAULT 0,
    refund_request_id VARCHAR(64) NOT NULL,
    plan_version      INT         NOT NULL,
    sequence_no       INT         NOT NULL,
    payment_id        VARCHAR(64) NOT NULL,
    refund_id         VARCHAR(64) NOT NULL,
    amount_minor      BIGINT      NOT NULL,
    UNIQUE KEY uk_refund_plan_seq (tenant_id, refund_request_id, plan_version, sequence_no),
    UNIQUE KEY uk_refund_plan_payment (tenant_id, refund_request_id, plan_version, payment_id),
    UNIQUE KEY uk_refund_plan_child (tenant_id, refund_id),
    CHECK (deleted = 0 AND plan_version = 1 AND sequence_no > 0 AND amount_minor > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE refund_order
(
    id                              VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_id                       VARCHAR(64)  NOT NULL,
    created_at                      DATETIME(6)  NOT NULL,
    updated_at                      DATETIME(6)  NOT NULL,
    deleted                         TINYINT      NOT NULL DEFAULT 0,
    refund_request_id               VARCHAR(64)  NOT NULL,
    payment_id                      VARCHAR(64)  NOT NULL,
    original_attempt_id             VARCHAR(64)  NOT NULL,
    binding_revision_id             VARCHAR(64)  NOT NULL,
    channel                         VARCHAR(24)  NOT NULL,
    account_scope_hash              CHAR(64)     NOT NULL,
    original_channel_request_no     VARCHAR(64)  NOT NULL,
    original_channel_transaction_no VARCHAR(128) NOT NULL,
    original_succeeded_at           DATETIME(6)  NOT NULL,
    submission_started_at           DATETIME(6)  NULL,
    channel_refund_no               VARCHAR(64)  NOT NULL,
    channel_refund_transaction_no   VARCHAR(128) NULL,
    amount_minor                    BIGINT       NOT NULL,
    currency                        CHAR(3)      NOT NULL,
    state                           VARCHAR(24)  NOT NULL,
    first_unknown_at                DATETIME(6)  NULL,
    terminal_evidence_id            VARCHAR(64)  NULL,
    next_query_at                   DATETIME(6)  NULL,
    query_sequence                  BIGINT       NOT NULL DEFAULT 0,
    succeeded_at                    DATETIME(6)  NULL,
    row_version                     BIGINT       NOT NULL DEFAULT 0,
    UNIQUE KEY uk_refund_channel_no (channel, account_scope_hash, channel_refund_no),
    KEY ix_refund_child_request (tenant_id, refund_request_id, id),
    KEY ix_refund_query (tenant_id, state, next_query_at, id),
    CHECK (deleted = 0 AND amount_minor > 0 AND currency = 'CNY' AND row_version >= 0 AND query_sequence >= 0),
    CHECK (state IN ('INIT', 'PROCESSING', 'UNKNOWN', 'SUCCEEDED', 'FAILED')),
    CHECK (state <> 'UNKNOWN' OR first_unknown_at IS NOT NULL),
    CHECK ((state = 'INIT' AND submission_started_at IS NULL) OR
           (state <> 'INIT' AND submission_started_at IS NOT NULL)),
    CHECK ((state IN ('SUCCEEDED', 'FAILED') AND terminal_evidence_id IS NOT NULL)
        OR (state IN ('INIT', 'PROCESSING', 'UNKNOWN') AND terminal_evidence_id IS NULL AND succeeded_at IS NULL)),
    CHECK (state <> 'SUCCEEDED' OR (succeeded_at IS NOT NULL AND channel_refund_transaction_no IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
