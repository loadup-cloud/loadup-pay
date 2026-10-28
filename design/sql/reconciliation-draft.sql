-- Design draft only. Not a Flyway migration and never applied by this task.
-- Dedicated reconciliation schema. BaseDO audit fields use the application/system local time.
CREATE TABLE reconciliation_batch
(
    id                   VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_id            VARCHAR(64)  NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    deleted              TINYINT      NOT NULL DEFAULT 0,
    caller_service_id    VARCHAR(64)  NOT NULL,
    request_key          VARCHAR(128) NOT NULL,
    request_digest       VARCHAR(64)  NOT NULL,
    channel_account_code VARCHAR(64)  NOT NULL,
    channel              VARCHAR(32)  NOT NULL,
    account_scope_hash   VARCHAR(64)  NOT NULL,
    bill_date            DATE         NOT NULL,
    statement_profile    VARCHAR(64)  NOT NULL,
    state                VARCHAR(32)  NOT NULL,
    channel_input_id     VARCHAR(64)  NULL,
    ledger_input_id      VARCHAR(64)  NULL,
    run_id               VARCHAR(64)  NULL,
    matched_count        BIGINT       NULL,
    difference_count     BIGINT       NULL,
    result_hash          VARCHAR(64)  NULL,
    row_version          BIGINT       NOT NULL,
    UNIQUE KEY uk_recon_batch_run (tenant_id, run_id),
    UNIQUE KEY uk_recon_batch_request (tenant_id, caller_service_id, request_key),
    KEY ix_recon_batch_scope (tenant_id, channel, account_scope_hash, bill_date, state, id),
    CHECK (deleted = 0 AND row_version >= 0 AND channel IN ('ALIPAY', 'WECHAT')),
    CHECK ((state = 'INIT' AND (channel_input_id IS NULL OR ledger_input_id IS NULL))
        OR (state IN ('READY', 'RUNNING', 'MATCHED', 'DIFFERENCES') AND channel_input_id IS NOT NULL AND
            ledger_input_id IS NOT NULL)),
    CHECK ((state IN ('INIT', 'READY') AND run_id IS NULL) OR
           (state IN ('RUNNING', 'MATCHED', 'DIFFERENCES') AND run_id IS NOT NULL)),
    CHECK ((state IN ('INIT', 'READY', 'RUNNING') AND matched_count IS NULL AND difference_count IS NULL AND
            result_hash IS NULL)
        OR (state = 'MATCHED' AND matched_count IS NOT NULL AND difference_count IS NOT NULL AND matched_count >= 0 AND
            difference_count = 0 AND result_hash IS NOT NULL)
        OR (state = 'DIFFERENCES' AND matched_count IS NOT NULL AND difference_count IS NOT NULL AND
            matched_count >= 0 AND difference_count > 0 AND result_hash IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE reconciliation_input
(
    id                   VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id            VARCHAR(64) NOT NULL,
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    deleted              TINYINT     NOT NULL DEFAULT 0,
    side                 VARCHAR(16) NOT NULL,
    channel              VARCHAR(32) NOT NULL,
    account_scope_hash   VARCHAR(64) NOT NULL,
    bill_date            DATE        NOT NULL,
    statement_profile    VARCHAR(64) NOT NULL,
    file_id              VARCHAR(64) NOT NULL,
    artifact_sha256      VARCHAR(64) NOT NULL,
    normalized_sha256    VARCHAR(64) NULL,
    source_capture_id    VARCHAR(64) NOT NULL,
    parser_revision      VARCHAR(64) NOT NULL,
    state                VARCHAR(32) NOT NULL,
    payment_count        BIGINT      NOT NULL DEFAULT 0,
    payment_amount_minor BIGINT      NOT NULL DEFAULT 0,
    refund_count         BIGINT      NOT NULL DEFAULT 0,
    refund_amount_minor  BIGINT      NOT NULL DEFAULT 0,
    rejection_code       VARCHAR(64) NULL,
    row_version          BIGINT      NOT NULL,
    UNIQUE KEY uk_recon_input_capture (tenant_id, side, source_capture_id, parser_revision),
    KEY ix_recon_input_scope (tenant_id, channel, account_scope_hash, bill_date),
    CHECK (deleted = 0 AND row_version >= 0 AND channel IN ('ALIPAY', 'WECHAT') AND side IN ('CHANNEL', 'LEDGER')),
    CHECK (payment_count >= 0 AND refund_count >= 0 AND payment_amount_minor >= payment_count AND
           refund_amount_minor >= refund_count),
    CHECK ((payment_count = 0 AND payment_amount_minor = 0) OR (payment_count > 0 AND payment_amount_minor > 0)),
    CHECK ((refund_count = 0 AND refund_amount_minor = 0) OR (refund_count > 0 AND refund_amount_minor > 0)),
    CHECK ((state = 'INIT' AND normalized_sha256 IS NULL AND rejection_code IS NULL)
        OR (state = 'SEALED' AND normalized_sha256 IS NOT NULL AND rejection_code IS NULL)
        OR (state = 'REJECTED' AND rejection_code IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE reconciliation_entry
(
    id                     VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_id              VARCHAR(64)  NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    deleted                TINYINT      NOT NULL DEFAULT 0,
    input_id               VARCHAR(64)  NOT NULL,
    record_number          BIGINT       NOT NULL,
    match_key_hash         VARCHAR(64)  NOT NULL,
    kind                   VARCHAR(16)  NOT NULL,
    channel_transaction_no VARCHAR(128) NOT NULL,
    channel_refund_no      VARCHAR(64)  NULL,
    merchant_payment_no    VARCHAR(64)  NOT NULL,
    amount_minor           BIGINT       NOT NULL,
    currency               VARCHAR(3)   NOT NULL,
    state                  VARCHAR(32)  NOT NULL,
    UNIQUE KEY uk_recon_entry_key (tenant_id, input_id, match_key_hash),
    UNIQUE KEY uk_recon_entry_ordinal (tenant_id, input_id, record_number),
    CHECK (deleted = 0 AND record_number > 0 AND amount_minor > 0 AND currency = 'CNY'),
    CHECK ((kind = 'PAYMENT' AND channel_refund_no IS NULL) OR (kind = 'REFUND' AND channel_refund_no IS NOT NULL)),
    CHECK (state IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'UNKNOWN'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE reconciliation_difference
(
    id                     VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_id              VARCHAR(64)  NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    deleted                TINYINT      NOT NULL DEFAULT 0,
    batch_id               VARCHAR(64)  NOT NULL,
    run_id                 VARCHAR(64)  NOT NULL,
    match_key_hash         VARCHAR(64)  NOT NULL,
    kind                   VARCHAR(16)  NOT NULL,
    channel_transaction_no VARCHAR(128) NOT NULL,
    channel_refund_no      VARCHAR(64)  NULL,
    channel_entry_id       VARCHAR(64)  NULL,
    ledger_entry_id        VARCHAR(64)  NULL,
    channel_only           TINYINT      NOT NULL,
    ledger_only            TINYINT      NOT NULL,
    amount_mismatch        TINYINT      NOT NULL,
    request_mismatch       TINYINT      NOT NULL,
    state_mismatch         TINYINT      NOT NULL,
    UNIQUE KEY uk_recon_difference_key (tenant_id, batch_id, match_key_hash),
    KEY ix_recon_difference_page (tenant_id, batch_id, kind, id),
    CHECK (deleted = 0 AND channel_only IN (0, 1) AND ledger_only IN (0, 1) AND amount_mismatch IN (0, 1)
        AND request_mismatch IN (0, 1) AND state_mismatch IN (0, 1)),
    CHECK ((kind = 'PAYMENT' AND channel_refund_no IS NULL) OR (kind = 'REFUND' AND channel_refund_no IS NOT NULL)),
    CHECK ((channel_only = 1 AND channel_entry_id IS NOT NULL AND ledger_entry_id IS NULL AND ledger_only = 0
        AND amount_mismatch = 0 AND request_mismatch = 0 AND state_mismatch = 0)
        OR (ledger_only = 1 AND ledger_entry_id IS NOT NULL AND channel_entry_id IS NULL AND channel_only = 0
            AND amount_mismatch = 0 AND request_mismatch = 0 AND state_mismatch = 0)
        OR (channel_entry_id IS NOT NULL AND ledger_entry_id IS NOT NULL AND channel_only = 0 AND ledger_only = 0
            AND (amount_mismatch = 1 OR request_mismatch = 1 OR state_mismatch = 1)))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

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
    CHECK (source_kind = 'MANUAL_UPLOAD'),
    CHECK (row_version = CASE WHEN state = 'INIT' THEN 0 ELSE 1 END),
    CHECK ((state = 'INIT' AND artifact_id IS NULL AND rejection_code IS NULL)
        OR (state = 'SEALED' AND artifact_id IS NOT NULL AND artifact_id = id AND rejection_code IS NULL)
        OR (state = 'REJECTED' AND artifact_id IS NULL AND rejection_code IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
