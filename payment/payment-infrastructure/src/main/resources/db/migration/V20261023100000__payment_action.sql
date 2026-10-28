CREATE TABLE payment_action
(
    id                  VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    deleted             TINYINT     NOT NULL DEFAULT 0,
    payment_id          VARCHAR(64) NOT NULL,
    channel             VARCHAR(32) NOT NULL,
    binding_revision_id VARCHAR(64) NOT NULL,
    account_scope_hash  VARCHAR(64) NOT NULL,
    channel_request_no  VARCHAR(64) NOT NULL,
    amount_minor        BIGINT      NOT NULL,
    currency            VARCHAR(3)  NOT NULL,
    execution_epoch     BIGINT      NOT NULL,
    source              VARCHAR(32) NOT NULL,
    receipt_id          VARCHAR(64) NULL,
    source_digest       VARCHAR(64) NOT NULL,
    issued_at           DATETIME(6) NOT NULL,
    expires_at          DATETIME(6) NOT NULL,
    action_kind         VARCHAR(32) NOT NULL,
    action_payload      LONGTEXT    NOT NULL,
    payload_hash        VARCHAR(64) NOT NULL,
    KEY ix_payment_action_owner (tenant_id, payment_id),
    UNIQUE KEY uk_payment_action_receipt (tenant_id, receipt_id),
    CHECK ((source = 'CHANNEL_RECEIPT' AND receipt_id IS NOT NULL AND action_kind <> 'ALIPAY_H5')
        OR (source = 'LOCAL_SIGNED' AND receipt_id IS NULL AND action_kind = 'ALIPAY_H5' AND
            source_digest = payload_hash)),
    CHECK (deleted = 0 AND amount_minor > 0 AND execution_epoch > 0 AND currency = 'CNY')
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

