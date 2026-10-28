-- Frozen official source intent, pinned private files and independent native download task.
CREATE TABLE reconciliation_statement_request
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
    binding_revision_id       VARCHAR(64)   NOT NULL,
    max_bytes                 INT           NOT NULL,
    task_id                   VARCHAR(64)   NOT NULL,
    state                     VARCHAR(32)   NOT NULL,
    row_version               BIGINT        NOT NULL,
    format                    VARCHAR(64)   NULL,
    request_sha256            VARCHAR(64)   NULL,
    official_hash_type        VARCHAR(32)   NULL,
    official_hash             VARCHAR(128)  NULL,
    received_epoch_micros     BIGINT        NULL,
    downloaded_epoch_micros   BIGINT        NULL,
    file_id                   VARCHAR(64)   NULL,
    storage_id                VARCHAR(1024) NULL,
    file_size                 BIGINT        NULL,
    source_sha256             VARCHAR(64)   NULL,
    proof_file_id             VARCHAR(64)   NULL,
    proof_storage_id          VARCHAR(1024) NULL,
    proof_file_size           BIGINT        NULL,
    proof_sha256              VARCHAR(64)   NULL,
    UNIQUE KEY uk_recon_statement_request (tenant_id, caller_service_id, request_key),
    UNIQUE KEY uk_recon_statement_task (tenant_id, task_id),
    KEY ix_recon_statement_active (tenant_id, batch_id, state),
    CHECK (deleted = 0 AND max_bytes > 0 AND max_bytes <= 16777216 AND channel = 'WECHAT' AND
           statement_profile = 'wechat-trade-all-20260116'),
    CHECK (row_version = CASE WHEN state = 'INIT' THEN 0 ELSE 1 END),
    CHECK ((state IN ('INIT', 'SUPERSEDED') AND downloaded_epoch_micros IS NULL AND file_id IS NULL AND
            file_size IS NULL AND format IS NULL AND official_hash IS NULL AND official_hash_type IS NULL AND
            proof_file_id IS NULL AND proof_file_size IS NULL AND proof_sha256 IS NULL AND proof_storage_id IS NULL AND
            received_epoch_micros IS NULL AND request_sha256 IS NULL AND source_sha256 IS NULL AND
            storage_id IS NULL) OR
           (state = 'READY' AND format IS NOT NULL AND request_sha256 IS NOT NULL AND official_hash_type IS NOT NULL AND
            official_hash IS NOT NULL AND received_epoch_micros IS NOT NULL AND downloaded_epoch_micros IS NOT NULL AND
            file_id IS NOT NULL AND storage_id IS NOT NULL AND file_size IS NOT NULL AND source_sha256 IS NOT NULL AND
            proof_file_id IS NOT NULL AND proof_storage_id IS NOT NULL AND proof_file_size IS NOT NULL AND
            proof_sha256 IS NOT NULL AND received_epoch_micros IS NOT NULL AND downloaded_epoch_micros IS NOT NULL AND
            file_size IS NOT NULL AND proof_file_size IS NOT NULL AND format = 'wechat-tradebill-grant-v1' AND
            official_hash_type = 'SHA1' AND file_id IS NOT NULL AND storage_id IS NOT NULL AND file_size > 0 AND
            file_size <= max_bytes AND source_sha256 IS NOT NULL AND proof_file_id IS NOT NULL AND
            proof_file_id <> file_id AND proof_storage_id IS NOT NULL AND proof_file_size > 0 AND
            proof_file_size <= 32768 AND proof_sha256 IS NOT NULL AND request_sha256 IS NOT NULL AND
            official_hash IS NOT NULL AND received_epoch_micros > 0 AND
            downloaded_epoch_micros >= received_epoch_micros))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
