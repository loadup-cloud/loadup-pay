-- Durable monotonic fences, local no-collection proofs and immutable command receipts.
CREATE TABLE payment_control_receipt
(
    id                        VARCHAR(64)  NOT NULL,
    tenant_id                 VARCHAR(64)  NOT NULL,
    created_at                DATETIME(6)  NOT NULL,
    updated_at                DATETIME(6)  NOT NULL,
    deleted                   TINYINT      NOT NULL DEFAULT 0,
    kind                      VARCHAR(24)  NOT NULL,
    target_id                 VARCHAR(64)  NOT NULL,
    order_id                  VARCHAR(64)  NOT NULL,
    payee_merchant_id         VARCHAR(64)  NOT NULL,
    caller_service_id         VARCHAR(64)  NOT NULL,
    request_key               VARCHAR(128) NOT NULL,
    request_digest            CHAR(64)     NOT NULL,
    requested_epoch           BIGINT       NOT NULL,
    reason_code               VARCHAR(64)  NULL,
    no_collection_evidence_id VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_control_request (tenant_id, kind, target_id, request_key),
    KEY ix_control_proof (tenant_id, target_id, kind, no_collection_evidence_id),
    CHECK (kind IN ('PARENT_FENCE', 'PAYMENT_CLOSE') AND requested_epoch > 0),
    CHECK ((kind = 'PARENT_FENCE' AND target_id = order_id AND reason_code IS NULL AND
            no_collection_evidence_id IS NULL)
        OR (kind = 'PAYMENT_CLOSE' AND reason_code IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

ALTER TABLE payment_order
    ADD CONSTRAINT ck_payment_no_collection CHECK (
        state NOT IN ('CLOSED', 'FAILED') OR terminal_evidence_id IS NOT NULL);
