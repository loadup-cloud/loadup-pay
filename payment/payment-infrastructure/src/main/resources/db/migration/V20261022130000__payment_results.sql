-- Y3 immutable receipts, financial uniqueness and initial refund capacity.
-- All business timestamps are local DATETIME(6). Raw signed bodies/credentials are never stored here.
ALTER TABLE payment_order ADD CONSTRAINT ck_payment_success_fact
    CHECK ((state = 'SUCCEEDED' AND succeeded_at IS NOT NULL AND terminal_evidence_id IS NOT NULL)
        OR (state <> 'SUCCEEDED' AND succeeded_at IS NULL));

CREATE TABLE payment_channel_receipt (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    payment_id VARCHAR(64) NOT NULL,
    attempt_id VARCHAR(64) NOT NULL,
    channel VARCHAR(24) NOT NULL,
    binding_revision_id VARCHAR(64) NOT NULL,
    account_scope_hash CHAR(64) NOT NULL,
    channel_request_no VARCHAR(64) NOT NULL,
    source VARCHAR(24) NOT NULL,
    receipt_key VARCHAR(128) NOT NULL,
    facts_digest CHAR(64) NOT NULL,
    source_digest CHAR(64) NOT NULL,
    observation_payload LONGTEXT NOT NULL,
    disposition VARCHAR(24) NOT NULL,
    reason_code VARCHAR(64) NULL,
    received_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_payment_channel_receipt (tenant_id, attempt_id, source, receipt_key),
    KEY ix_payment_receipt (tenant_id, payment_id, received_at, id),
    CHECK (deleted = 0),
    CHECK (source IN ('SUBMIT_RESPONSE','QUERY_RESPONSE','CALLBACK')),
    CHECK (disposition IN ('APPLIED','IGNORED','CONFLICT')),
    CHECK (disposition <> 'CONFLICT' OR reason_code IS NOT NULL),
    CHECK (JSON_VALID(observation_payload) AND CHAR_LENGTH(observation_payload) <= 32768)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE payment_result_anomaly (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    payment_id VARCHAR(64) NOT NULL,
    attempt_id VARCHAR(64) NOT NULL,
    receipt_id VARCHAR(64) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    facts_digest CHAR(64) NOT NULL,
    observation_payload LONGTEXT NOT NULL,
    received_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_result_anomaly (tenant_id, receipt_id, facts_digest, reason_code),
    KEY ix_result_anomaly_payment (tenant_id, payment_id, received_at, id),
    CHECK (deleted = 0),
    CHECK (JSON_VALID(observation_payload) AND CHAR_LENGTH(observation_payload) <= 32768)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE channel_transaction_claim (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    payment_id VARCHAR(64) NOT NULL,
    attempt_id VARCHAR(64) NOT NULL,
    channel VARCHAR(24) NOT NULL,
    binding_revision_id VARCHAR(64) NOT NULL,
    account_scope_hash CHAR(64) NOT NULL,
    channel_request_no VARCHAR(64) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    channel_transaction_no VARCHAR(128) NOT NULL,
    succeeded_at DATETIME(6) NOT NULL,
    receipt_id VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_channel_transaction (channel, account_scope_hash, channel_transaction_no),
    UNIQUE KEY uk_claim_attempt (tenant_id, attempt_id),
    UNIQUE KEY uk_claim_payment (tenant_id, payment_id),
    CHECK (deleted = 0 AND amount_minor > 0 AND currency = 'CNY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE payment_refund_capacity (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    payment_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    payee_merchant_id VARCHAR(64) NOT NULL,
    succeeded_at DATETIME(6) NOT NULL,
    succeeded_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    refunded_minor BIGINT NOT NULL DEFAULT 0,
    reserved_minor BIGINT NOT NULL DEFAULT 0,
    row_version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_refund_capacity_payment (tenant_id, payment_id),
    KEY ix_refund_capacity_fifo (tenant_id, order_id, succeeded_at, payment_id),
    CHECK (deleted = 0 AND succeeded_minor > 0 AND currency = 'CNY' AND row_version >= 0),
    CHECK (refunded_minor >= 0 AND reserved_minor >= 0
        AND refunded_minor <= succeeded_minor AND reserved_minor <= succeeded_minor - refunded_minor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
