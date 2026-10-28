-- Local import intent and provenance; File/Transfer metadata share this service's DataSource.
CREATE TABLE reconciliation_import
(
    id                        VARCHAR(64)   NOT NULL PRIMARY KEY,
    tenant_id                 VARCHAR(64)   NOT NULL,
    created_at                DATETIME(6)   NOT NULL,
    updated_at                DATETIME(6)   NOT NULL,
    deleted                   TINYINT       NOT NULL DEFAULT 0,
    batch_id                  VARCHAR(64)   NOT NULL,
    caller_service_id         VARCHAR(64)   NOT NULL,
    request_key               VARCHAR(128)  NOT NULL,
    request_digest            VARCHAR(64)   NOT NULL,
    channel                   VARCHAR(32)   NOT NULL,
    account_scope_hash        VARCHAR(64)   NOT NULL,
    bill_date                 DATE          NOT NULL,
    statement_profile         VARCHAR(64)   NOT NULL,
    statement_merchant_id     VARCHAR(64)   NOT NULL,
    statement_sub_merchant_id VARCHAR(64)   NULL,
    file_id                   VARCHAR(64)   NOT NULL,
    storage_id                VARCHAR(1024) NOT NULL,
    file_size                 BIGINT        NOT NULL,
    source_sha256             VARCHAR(64)   NOT NULL,
    source_kind               VARCHAR(32)   NOT NULL,
    task_id                   VARCHAR(64)   NOT NULL,
    state                     VARCHAR(32)   NOT NULL,
    artifact_id               VARCHAR(64)   NULL,
    rejection_code            VARCHAR(64)   NULL,
    row_version               BIGINT        NOT NULL,
    UNIQUE KEY uk_recon_import_request (tenant_id, caller_service_id, request_key),
    UNIQUE KEY uk_recon_import_task (tenant_id, task_id),
    KEY ix_recon_import_active (tenant_id, batch_id, state),
    CHECK (deleted = 0 AND row_version >= 0 AND file_size > 0 AND channel IN ('ALIPAY', 'WECHAT')),
    CHECK (source_kind IN ('MANUAL_UPLOAD', 'OFFICIAL_DOWNLOAD')),
    CHECK (row_version = CASE WHEN state = 'INIT' THEN 0 ELSE 1 END),
    CHECK ((state = 'INIT' AND artifact_id IS NULL AND rejection_code IS NULL)
        OR (state = 'SEALED' AND artifact_id IS NOT NULL AND artifact_id = id AND rejection_code IS NULL)
        OR (state = 'REJECTED' AND artifact_id IS NULL AND rejection_code IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
