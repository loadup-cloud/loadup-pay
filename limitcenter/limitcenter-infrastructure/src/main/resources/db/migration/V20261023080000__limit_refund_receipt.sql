CREATE TABLE limit_refund_receipt (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    caller_service_id VARCHAR(64) NOT NULL,
    operation_id VARCHAR(64) NOT NULL,
    payment_id VARCHAR(64) NOT NULL,
    refund_id VARCHAR(64) NOT NULL,
    refund_version BIGINT NOT NULL,
    business_event_id VARCHAR(64) NOT NULL,
    refund_evidence_id VARCHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    cumulative_payment_refunded_minor BIGINT NOT NULL,
    verified_payment_refunded_minor BIGINT NOT NULL,
    verified_payment_refund_version BIGINT NOT NULL,
    result VARCHAR(24) NOT NULL,
    restored_amount_minor BIGINT NOT NULL,
    restored_payment_count TINYINT NOT NULL,
    accepted_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_limit_refund_receipt_refund (tenant_id, refund_id),
    KEY ix_limit_refund_receipt_operation (tenant_id, operation_id),
    CHECK (
        deleted = 0
        AND refund_version > 0
        AND cumulative_payment_refunded_minor > 0
        AND verified_payment_refunded_minor >= cumulative_payment_refunded_minor
        AND verified_payment_refund_version > 0
        AND restored_amount_minor >= 0
        AND restored_amount_minor <= verified_payment_refunded_minor
        AND restored_payment_count IN (0, 1)
        AND (
            (restored_amount_minor > 0 OR restored_payment_count = 1) AND result = 'RESTORED'
            OR restored_amount_minor = 0 AND restored_payment_count = 0 AND result = 'NO_ACTION'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
