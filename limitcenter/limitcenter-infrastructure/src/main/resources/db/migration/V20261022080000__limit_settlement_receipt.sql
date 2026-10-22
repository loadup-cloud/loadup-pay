CREATE TABLE limit_settlement_receipt (
    id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    operation_id VARCHAR(64) NOT NULL,
    payment_id VARCHAR(64) NOT NULL,
    kind VARCHAR(24) NOT NULL,
    business_event_id VARCHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    payment_version BIGINT NOT NULL,
    evidence_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_limit_settlement_operation (tenant_id,operation_id),
    UNIQUE KEY uk_limit_settlement_event (tenant_id,business_event_id),
    CHECK (kind IN ('CONFIRM','RELEASE') AND payment_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
