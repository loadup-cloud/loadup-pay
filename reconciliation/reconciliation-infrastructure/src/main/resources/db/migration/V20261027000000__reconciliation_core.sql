-- PostgreSQL initial schema; all historical changes are folded into the table definitions.

CREATE TABLE reconciliation_batch
(
    id                   VARCHAR(64) COLLATE "C"  NOT NULL PRIMARY KEY,
    tenant_id            VARCHAR(64) COLLATE "C"  NOT NULL,
    created_at           TIMESTAMP(6) WITHOUT TIME ZONE  NOT NULL,
    updated_at           TIMESTAMP(6) WITHOUT TIME ZONE  NOT NULL,
    deleted              SMALLINT      NOT NULL DEFAULT 0,
    caller_service_id    VARCHAR(64) COLLATE "C"  NOT NULL,
    request_key          VARCHAR(128) COLLATE "C" NOT NULL,
    request_digest       VARCHAR(64) COLLATE "C"  NOT NULL,
    channel_account_code VARCHAR(64) COLLATE "C"  NOT NULL,
    channel              VARCHAR(32) COLLATE "C"  NOT NULL,
    account_scope_hash   VARCHAR(64) COLLATE "C"  NOT NULL,
    bill_date            DATE         NOT NULL,
    statement_profile    VARCHAR(64) COLLATE "C"  NOT NULL,
    state                VARCHAR(32) COLLATE "C"  NOT NULL,
    channel_input_id     VARCHAR(64) COLLATE "C"  NULL,
    ledger_input_id      VARCHAR(64) COLLATE "C"  NULL,
    run_id               VARCHAR(64) COLLATE "C"  NULL,
    matched_count        BIGINT       NULL,
    difference_count     BIGINT       NULL,
    result_hash          VARCHAR(64) COLLATE "C"  NULL,
    row_version          BIGINT       NOT NULL,
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
            matched_count >= 0 AND difference_count > 0 AND result_hash IS NOT NULL)),
    CHECK (deleted IN (0, 1))
);

CREATE UNIQUE INDEX uk_recon_batch_run ON reconciliation_batch (tenant_id, run_id);

CREATE UNIQUE INDEX uk_recon_batch_request ON reconciliation_batch (tenant_id, caller_service_id, request_key);

CREATE INDEX ix_recon_batch_scope ON reconciliation_batch (tenant_id, channel, account_scope_hash, bill_date, state, id);

CREATE TABLE reconciliation_input
(
    id                   VARCHAR(64) COLLATE "C" NOT NULL PRIMARY KEY,
    tenant_id            VARCHAR(64) COLLATE "C" NOT NULL,
    created_at           TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at           TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted              SMALLINT     NOT NULL DEFAULT 0,
    side                 VARCHAR(16) COLLATE "C" NOT NULL,
    channel              VARCHAR(32) COLLATE "C" NOT NULL,
    account_scope_hash   VARCHAR(64) COLLATE "C" NOT NULL,
    bill_date            DATE        NOT NULL,
    statement_profile    VARCHAR(64) COLLATE "C" NOT NULL,
    file_id              VARCHAR(64) COLLATE "C" NOT NULL,
    artifact_sha256      VARCHAR(64) COLLATE "C" NOT NULL,
    normalized_sha256    VARCHAR(64) COLLATE "C" NULL,
    source_capture_id    VARCHAR(64) COLLATE "C" NOT NULL,
    parser_revision      VARCHAR(64) COLLATE "C" NOT NULL,
    state                VARCHAR(32) COLLATE "C" NOT NULL,
    payment_count        BIGINT      NOT NULL DEFAULT 0,
    payment_amount_minor BIGINT      NOT NULL DEFAULT 0,
    refund_count         BIGINT      NOT NULL DEFAULT 0,
    refund_amount_minor  BIGINT      NOT NULL DEFAULT 0,
    rejection_code       VARCHAR(64) COLLATE "C" NULL,
    row_version          BIGINT      NOT NULL,
    CHECK (deleted = 0 AND row_version >= 0 AND channel IN ('ALIPAY', 'WECHAT') AND side IN ('CHANNEL', 'LEDGER')),
    CHECK (payment_count >= 0 AND refund_count >= 0 AND payment_amount_minor >= payment_count AND
           refund_amount_minor >= refund_count),
    CHECK ((payment_count = 0 AND payment_amount_minor = 0) OR (payment_count > 0 AND payment_amount_minor > 0)),
    CHECK ((refund_count = 0 AND refund_amount_minor = 0) OR (refund_count > 0 AND refund_amount_minor > 0)),
    CHECK ((state = 'INIT' AND normalized_sha256 IS NULL AND rejection_code IS NULL)
        OR (state = 'SEALED' AND normalized_sha256 IS NOT NULL AND rejection_code IS NULL)
        OR (state = 'REJECTED' AND rejection_code IS NOT NULL)),
    CHECK (deleted IN (0, 1))
);

CREATE UNIQUE INDEX uk_recon_input_capture ON reconciliation_input (tenant_id, side, source_capture_id, parser_revision);

CREATE INDEX ix_recon_input_scope ON reconciliation_input (tenant_id, channel, account_scope_hash, bill_date);

CREATE TABLE reconciliation_entry
(
    id                     VARCHAR(64) COLLATE "C"  NOT NULL PRIMARY KEY,
    tenant_id              VARCHAR(64) COLLATE "C"  NOT NULL,
    created_at             TIMESTAMP(6) WITHOUT TIME ZONE  NOT NULL,
    updated_at             TIMESTAMP(6) WITHOUT TIME ZONE  NOT NULL,
    deleted                SMALLINT      NOT NULL DEFAULT 0,
    input_id               VARCHAR(64) COLLATE "C"  NOT NULL,
    record_number          BIGINT       NOT NULL,
    match_key_hash         VARCHAR(64) COLLATE "C"  NOT NULL,
    kind                   VARCHAR(16) COLLATE "C"  NOT NULL,
    channel_transaction_no VARCHAR(128) COLLATE "C" NOT NULL,
    channel_refund_no      VARCHAR(64) COLLATE "C"  NULL,
    merchant_payment_no    VARCHAR(64) COLLATE "C"  NOT NULL,
    amount_minor           BIGINT       NOT NULL,
    currency               VARCHAR(3) COLLATE "C"   NOT NULL,
    state                  VARCHAR(32) COLLATE "C"  NOT NULL,
    CHECK (deleted = 0 AND record_number > 0 AND amount_minor > 0 AND currency = 'CNY'),
    CHECK ((kind = 'PAYMENT' AND channel_refund_no IS NULL) OR (kind = 'REFUND' AND channel_refund_no IS NOT NULL)),
    CHECK (state IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    CHECK (deleted IN (0, 1))
);

CREATE UNIQUE INDEX uk_recon_entry_key ON reconciliation_entry (tenant_id, input_id, match_key_hash);

CREATE UNIQUE INDEX uk_recon_entry_ordinal ON reconciliation_entry (tenant_id, input_id, record_number);

CREATE TABLE reconciliation_difference
(
    id                     VARCHAR(64) COLLATE "C"  NOT NULL PRIMARY KEY,
    tenant_id              VARCHAR(64) COLLATE "C"  NOT NULL,
    created_at             TIMESTAMP(6) WITHOUT TIME ZONE  NOT NULL,
    updated_at             TIMESTAMP(6) WITHOUT TIME ZONE  NOT NULL,
    deleted                SMALLINT      NOT NULL DEFAULT 0,
    batch_id               VARCHAR(64) COLLATE "C"  NOT NULL,
    run_id                 VARCHAR(64) COLLATE "C"  NOT NULL,
    match_key_hash         VARCHAR(64) COLLATE "C"  NOT NULL,
    kind                   VARCHAR(16) COLLATE "C"  NOT NULL,
    channel_transaction_no VARCHAR(128) COLLATE "C" NOT NULL,
    channel_refund_no      VARCHAR(64) COLLATE "C"  NULL,
    channel_entry_id       VARCHAR(64) COLLATE "C"  NULL,
    ledger_entry_id        VARCHAR(64) COLLATE "C"  NULL,
    channel_only           SMALLINT      NOT NULL,
    ledger_only            SMALLINT      NOT NULL,
    amount_mismatch        SMALLINT      NOT NULL,
    request_mismatch       SMALLINT      NOT NULL,
    state_mismatch         SMALLINT      NOT NULL,
    CHECK (deleted = 0 AND channel_only IN (0, 1) AND ledger_only IN (0, 1) AND amount_mismatch IN (0, 1)
        AND request_mismatch IN (0, 1) AND state_mismatch IN (0, 1)),
    CHECK ((kind = 'PAYMENT' AND channel_refund_no IS NULL) OR (kind = 'REFUND' AND channel_refund_no IS NOT NULL)),
    CHECK ((channel_only = 1 AND channel_entry_id IS NOT NULL AND ledger_entry_id IS NULL AND ledger_only = 0
        AND amount_mismatch = 0 AND request_mismatch = 0 AND state_mismatch = 0)
        OR (ledger_only = 1 AND ledger_entry_id IS NOT NULL AND channel_entry_id IS NULL AND channel_only = 0
            AND amount_mismatch = 0 AND request_mismatch = 0 AND state_mismatch = 0)
        OR (channel_entry_id IS NOT NULL AND ledger_entry_id IS NOT NULL AND channel_only = 0 AND ledger_only = 0
            AND (amount_mismatch = 1 OR request_mismatch = 1 OR state_mismatch = 1))),
    CHECK (deleted IN (0, 1))
);

CREATE UNIQUE INDEX uk_recon_difference_key ON reconciliation_difference (tenant_id, batch_id, match_key_hash);

CREATE INDEX ix_recon_difference_page ON reconciliation_difference (tenant_id, batch_id, kind, id);

CREATE TABLE reconciliation_import
(
    id                        VARCHAR(64) COLLATE "C"   NOT NULL PRIMARY KEY,
    tenant_id                 VARCHAR(64) COLLATE "C"   NOT NULL,
    created_at                TIMESTAMP(6) WITHOUT TIME ZONE   NOT NULL,
    updated_at                TIMESTAMP(6) WITHOUT TIME ZONE   NOT NULL,
    deleted                   SMALLINT       NOT NULL DEFAULT 0,
    batch_id                  VARCHAR(64) COLLATE "C"   NOT NULL,
    caller_service_id         VARCHAR(64) COLLATE "C"   NOT NULL,
    request_key               VARCHAR(128) COLLATE "C"  NOT NULL,
    request_digest            VARCHAR(64) COLLATE "C"   NOT NULL,
    channel                   VARCHAR(32) COLLATE "C"   NOT NULL,
    account_scope_hash        VARCHAR(64) COLLATE "C"   NOT NULL,
    bill_date                 DATE          NOT NULL,
    statement_profile         VARCHAR(64) COLLATE "C"   NOT NULL,
    statement_merchant_id     VARCHAR(64) COLLATE "C"   NOT NULL,
    statement_sub_merchant_id VARCHAR(64) COLLATE "C"   NULL,
    file_id                   VARCHAR(64) COLLATE "C"   NOT NULL,
    storage_id                VARCHAR(1024) COLLATE "C" NOT NULL,
    file_size                 BIGINT        NOT NULL,
    source_sha256             VARCHAR(64) COLLATE "C"   NOT NULL,
    source_kind               VARCHAR(32) COLLATE "C"   NOT NULL,
    task_id                   VARCHAR(64) COLLATE "C"   NOT NULL,
    state                     VARCHAR(32) COLLATE "C"   NOT NULL,
    artifact_id               VARCHAR(64) COLLATE "C"   NULL,
    rejection_code            VARCHAR(64) COLLATE "C"   NULL,
    row_version               BIGINT        NOT NULL,
    CHECK (deleted = 0 AND row_version >= 0 AND file_size > 0 AND channel IN ('ALIPAY', 'WECHAT')),
    CHECK (source_kind IN ('MANUAL_UPLOAD', 'OFFICIAL_DOWNLOAD')),
    CHECK (row_version = CASE WHEN state = 'INIT' THEN 0 ELSE 1 END),
    CHECK ((state = 'INIT' AND artifact_id IS NULL AND rejection_code IS NULL)
        OR (state = 'SEALED' AND artifact_id IS NOT NULL AND artifact_id = id AND rejection_code IS NULL)
        OR (state = 'REJECTED' AND artifact_id IS NULL AND rejection_code IS NOT NULL)),
    CHECK (deleted IN (0, 1))
);

CREATE UNIQUE INDEX uk_recon_import_request ON reconciliation_import (tenant_id, caller_service_id, request_key);

CREATE UNIQUE INDEX uk_recon_import_task ON reconciliation_import (tenant_id, task_id);

CREATE INDEX ix_recon_import_active ON reconciliation_import (tenant_id, batch_id, state);

CREATE TABLE reconciliation_statement_request
(
    id                        VARCHAR(64) COLLATE "C"   NOT NULL PRIMARY KEY,
    tenant_id                 VARCHAR(64) COLLATE "C"   NOT NULL,
    created_at                TIMESTAMP(6) WITHOUT TIME ZONE   NOT NULL,
    updated_at                TIMESTAMP(6) WITHOUT TIME ZONE   NOT NULL,
    deleted                   SMALLINT       NOT NULL DEFAULT 0,
    batch_id                  VARCHAR(64) COLLATE "C"   NOT NULL,
    caller_service_id         VARCHAR(64) COLLATE "C"   NOT NULL,
    request_key               VARCHAR(128) COLLATE "C"  NOT NULL,
    request_digest            VARCHAR(64) COLLATE "C"   NOT NULL,
    channel                   VARCHAR(32) COLLATE "C"   NOT NULL,
    account_scope_hash        VARCHAR(64) COLLATE "C"   NOT NULL,
    bill_date                 DATE          NOT NULL,
    statement_profile         VARCHAR(64) COLLATE "C"   NOT NULL,
    statement_merchant_id     VARCHAR(64) COLLATE "C"   NOT NULL,
    statement_sub_merchant_id VARCHAR(64) COLLATE "C"   NULL,
    binding_revision_id       VARCHAR(64) COLLATE "C"   NOT NULL,
    max_bytes                 INT           NOT NULL,
    task_id                   VARCHAR(64) COLLATE "C"   NOT NULL,
    state                     VARCHAR(32) COLLATE "C"   NOT NULL,
    row_version               BIGINT        NOT NULL,
    format                    VARCHAR(64) COLLATE "C"   NULL,
    request_sha256            VARCHAR(64) COLLATE "C"   NULL,
    official_hash_type        VARCHAR(32) COLLATE "C"   NULL,
    official_hash             VARCHAR(128) COLLATE "C"  NULL,
    received_epoch_micros     BIGINT        NULL,
    downloaded_epoch_micros   BIGINT        NULL,
    file_id                   VARCHAR(64) COLLATE "C"   NULL,
    storage_id                VARCHAR(1024) COLLATE "C" NULL,
    file_size                 BIGINT        NULL,
    source_sha256             VARCHAR(64) COLLATE "C"   NULL,
    proof_file_id             VARCHAR(64) COLLATE "C"   NULL,
    proof_storage_id          VARCHAR(1024) COLLATE "C" NULL,
    proof_file_size           BIGINT        NULL,
    proof_sha256              VARCHAR(64) COLLATE "C"   NULL,
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
            downloaded_epoch_micros >= received_epoch_micros)),
    CHECK (deleted IN (0, 1))
);

CREATE UNIQUE INDEX uk_recon_statement_request ON reconciliation_statement_request (tenant_id, caller_service_id, request_key);

CREATE UNIQUE INDEX uk_recon_statement_task ON reconciliation_statement_request (tenant_id, task_id);

CREATE INDEX ix_recon_statement_active ON reconciliation_statement_request (tenant_id, batch_id, state);

CREATE TABLE reconciliation_review
(
    id             VARCHAR(64) COLLATE "C" NOT NULL PRIMARY KEY,
    tenant_id      VARCHAR(64) COLLATE "C" NOT NULL,
    created_at     TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at     TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted        SMALLINT     NOT NULL DEFAULT 0,
    batch_id       VARCHAR(64) COLLATE "C" NOT NULL,
    run_id         VARCHAR(64) COLLATE "C" NOT NULL,
    result_hash    VARCHAR(64) COLLATE "C" NOT NULL,
    match_key_hash VARCHAR(64) COLLATE "C" NOT NULL,
    state          VARCHAR(32) COLLATE "C" NOT NULL,
    proposal_id    VARCHAR(64) COLLATE "C" NOT NULL,
    last_event_id  VARCHAR(64) COLLATE "C" NOT NULL,
    row_version    BIGINT      NOT NULL,
    CHECK (deleted = 0),
    CHECK ((state = 'PROPOSED' AND row_version > 0 AND MOD(row_version, 2) = 1) OR
           (state IN ('APPROVED', 'REJECTED') AND row_version >= 2 AND MOD(row_version, 2) = 0)),
    CHECK (deleted IN (0, 1))
);

CREATE UNIQUE INDEX uk_recon_review_last_event ON reconciliation_review (tenant_id, last_event_id);

CREATE TABLE reconciliation_review_proposal
(
    id                  VARCHAR(64) COLLATE "C"   NOT NULL PRIMARY KEY,
    tenant_id           VARCHAR(64) COLLATE "C"   NOT NULL,
    created_at          TIMESTAMP(6) WITHOUT TIME ZONE   NOT NULL,
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE   NOT NULL,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    difference_id       VARCHAR(64) COLLATE "C"   NOT NULL,
    batch_id            VARCHAR(64) COLLATE "C"   NOT NULL,
    run_id              VARCHAR(64) COLLATE "C"   NOT NULL,
    result_hash         VARCHAR(64) COLLATE "C"   NOT NULL,
    match_key_hash      VARCHAR(64) COLLATE "C"   NOT NULL,
    proposer_service_id VARCHAR(64) COLLATE "C"   NOT NULL,
    conclusion          VARCHAR(64) COLLATE "C"   NOT NULL,
    note                VARCHAR(1024) COLLATE "C" NULL,
    evidence_file_id    VARCHAR(64) COLLATE "C"   NULL,
    evidence_storage_id VARCHAR(1024) COLLATE "C" NULL,
    evidence_size       BIGINT        NULL,
    evidence_sha256     VARCHAR(64) COLLATE "C"   NULL,
    proposed_at         TIMESTAMP(6) WITHOUT TIME ZONE   NOT NULL,
    CHECK (deleted = 0),
    CHECK (conclusion IN ('CONFIRMED_DIFFERENCE', 'TIMING_DIFFERENCE', 'INPUT_DEFECT', 'NEEDS_SOURCE_VERIFICATION') AND
           note IS NOT NULL),
    CHECK ((evidence_file_id IS NULL AND evidence_storage_id IS NULL AND evidence_size IS NULL AND
            evidence_sha256 IS NULL AND conclusion <> 'INPUT_DEFECT') OR
           (evidence_file_id IS NOT NULL AND evidence_storage_id IS NOT NULL AND evidence_size IS NOT NULL AND
            evidence_size > 0 AND evidence_sha256 IS NOT NULL)),
    CHECK (deleted IN (0, 1))
);

CREATE INDEX ix_recon_review_proposal ON reconciliation_review_proposal (tenant_id, difference_id, id);

CREATE TABLE reconciliation_review_event
(
    id                VARCHAR(64) COLLATE "C"   NOT NULL PRIMARY KEY,
    tenant_id         VARCHAR(64) COLLATE "C"   NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE   NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE   NOT NULL,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    difference_id     VARCHAR(64) COLLATE "C"   NOT NULL,
    batch_id          VARCHAR(64) COLLATE "C"   NOT NULL,
    run_id            VARCHAR(64) COLLATE "C"   NOT NULL,
    result_hash       VARCHAR(64) COLLATE "C"   NOT NULL,
    match_key_hash    VARCHAR(64) COLLATE "C"   NOT NULL,
    caller_service_id VARCHAR(64) COLLATE "C"   NOT NULL,
    request_key       VARCHAR(128) COLLATE "C"  NOT NULL,
    request_digest    VARCHAR(64) COLLATE "C"   NOT NULL,
    operation         VARCHAR(32) COLLATE "C"   NOT NULL,
    proposal_id       VARCHAR(64) COLLATE "C"   NOT NULL,
    state             VARCHAR(32) COLLATE "C"   NOT NULL,
    source_version    BIGINT        NOT NULL,
    note              VARCHAR(1024) COLLATE "C" NULL,
    occurred_at       TIMESTAMP(6) WITHOUT TIME ZONE   NOT NULL,
    CHECK (deleted = 0),
    CHECK ((operation = 'PROPOSE' AND state = 'PROPOSED' AND source_version > 0 AND MOD(source_version, 2) = 1 AND
            note IS NULL) OR (operation IN ('APPROVE', 'REJECT') AND
                              state = CASE WHEN operation = 'APPROVE' THEN 'APPROVED' ELSE 'REJECTED' END AND
                              source_version >= 2 AND MOD(source_version, 2) = 0 AND note IS NOT NULL)),
    CHECK (deleted IN (0, 1))
);

CREATE UNIQUE INDEX uk_recon_review_command ON reconciliation_review_event (tenant_id, caller_service_id, request_key);

CREATE UNIQUE INDEX uk_recon_review_sequence ON reconciliation_review_event (tenant_id, difference_id, source_version);

CREATE TABLE reconciliation_verification_request
(
    id                              VARCHAR(64) COLLATE "C"  NOT NULL PRIMARY KEY,
    tenant_id                       VARCHAR(64) COLLATE "C"  NOT NULL,
    created_at                      TIMESTAMP(6) WITHOUT TIME ZONE  NOT NULL,
    updated_at                      TIMESTAMP(6) WITHOUT TIME ZONE  NOT NULL,
    deleted                         SMALLINT      NOT NULL DEFAULT 0,
    difference_id                   VARCHAR(64) COLLATE "C"  NOT NULL,
    batch_id                        VARCHAR(64) COLLATE "C"  NOT NULL,
    run_id                          VARCHAR(64) COLLATE "C"  NOT NULL,
    result_hash                     VARCHAR(64) COLLATE "C"  NOT NULL,
    match_key_hash                  VARCHAR(64) COLLATE "C"  NOT NULL,
    proposal_id                     VARCHAR(64) COLLATE "C"  NOT NULL,
    approval_event_id               VARCHAR(64) COLLATE "C"  NOT NULL,
    review_version                  BIGINT       NOT NULL,
    caller_service_id               VARCHAR(64) COLLATE "C"  NOT NULL,
    request_key                     VARCHAR(128) COLLATE "C" NOT NULL,
    request_digest                  VARCHAR(64) COLLATE "C"  NOT NULL,
    channel                         VARCHAR(64) COLLATE "C"  NOT NULL,
    account_scope_hash              VARCHAR(64) COLLATE "C"  NOT NULL,
    bill_date                       DATE         NOT NULL,
    statement_profile               VARCHAR(64) COLLATE "C"  NOT NULL,
    kind                            VARCHAR(64) COLLATE "C"  NOT NULL,
    channel_transaction_no          VARCHAR(128) COLLATE "C" NOT NULL,
    channel_refund_no               VARCHAR(64) COLLATE "C"  NULL,
    merchant_payment_no             VARCHAR(64) COLLATE "C"  NOT NULL,
    delivery_id                     VARCHAR(64) COLLATE "C"  NOT NULL,
    state                           VARCHAR(64) COLLATE "C"  NOT NULL,
    row_version                     BIGINT       NOT NULL,
    disposition                     VARCHAR(64) COLLATE "C"  NULL,
    binding_revision_id             VARCHAR(64) COLLATE "C"  NULL,
    payment_id                      VARCHAR(64) COLLATE "C"  NULL,
    attempt_id                      VARCHAR(64) COLLATE "C"  NULL,
    refund_id                       VARCHAR(64) COLLATE "C"  NULL,
    source_version                  BIGINT       NULL,
    local_state                     VARCHAR(64) COLLATE "C"  NULL,
    outcome                         VARCHAR(64) COLLATE "C"  NULL,
    amount_minor                    BIGINT       NULL,
    currency                        VARCHAR(64) COLLATE "C"  NULL,
    observed_channel_transaction_no VARCHAR(128) COLLATE "C" NULL,
    observed_refund_transaction_no  VARCHAR(128) COLLATE "C" NULL,
    succeeded_epoch_micros          BIGINT       NULL,
    source_digest                   VARCHAR(64) COLLATE "C"  NULL,
    started_epoch_micros            BIGINT       NULL,
    finished_epoch_micros           BIGINT       NULL,
    accepted_epoch_micros           BIGINT       NULL,
    CHECK (deleted = 0 AND review_version >= 2 AND MOD(review_version, 2) = 0),
    CHECK ((kind = 'PAYMENT' AND channel_refund_no IS NULL) OR (kind = 'REFUND' AND channel_refund_no IS NOT NULL)),
    CHECK ((state = 'INIT' AND row_version = 0 AND accepted_epoch_micros IS NULL AND disposition IS NULL AND
            binding_revision_id IS NULL AND payment_id IS NULL AND attempt_id IS NULL AND refund_id IS NULL AND
            source_version IS NULL AND local_state IS NULL AND outcome IS NULL AND amount_minor IS NULL AND
            currency IS NULL AND observed_channel_transaction_no IS NULL AND observed_refund_transaction_no IS NULL AND
            succeeded_epoch_micros IS NULL AND source_digest IS NULL AND started_epoch_micros IS NULL AND
            finished_epoch_micros IS NULL) OR (state = 'RECORDED' AND row_version = 1 AND disposition IS NOT NULL AND
                                               disposition IN
                                               ('OBSERVED', 'NO_LOCAL_TARGET', 'NOT_SUBMITTED', 'AMBIGUOUS_TARGET') AND
                                               started_epoch_micros IS NOT NULL AND started_epoch_micros > 0 AND
                                               finished_epoch_micros IS NOT NULL AND
                                               finished_epoch_micros >= started_epoch_micros)),
    CHECK (state = 'INIT' OR
           (disposition = 'OBSERVED' AND binding_revision_id IS NOT NULL AND payment_id IS NOT NULL AND
            attempt_id IS NOT NULL
               AND source_version IS NOT NULL AND source_version >= 0 AND local_state IS NOT NULL AND
            outcome IS NOT NULL AND amount_minor IS NOT NULL AND amount_minor > 0
               AND currency IS NOT NULL AND currency = 'CNY' AND source_digest IS NOT NULL)
        OR (disposition <> 'OBSERVED' AND binding_revision_id IS NULL AND payment_id IS NULL AND attempt_id IS NULL AND
            refund_id IS NULL AND source_version IS NULL
            AND local_state IS NULL AND outcome IS NULL AND amount_minor IS NULL AND currency IS NULL AND
            observed_channel_transaction_no IS NULL
            AND observed_refund_transaction_no IS NULL AND succeeded_epoch_micros IS NULL AND
            accepted_epoch_micros IS NULL AND source_digest IS NULL)),
    CHECK (state = 'INIT' OR disposition <> 'OBSERVED' OR
           ((kind = 'PAYMENT' AND refund_id IS NULL AND observed_refund_transaction_no IS NULL AND
             accepted_epoch_micros IS NULL
               AND outcome IN ('ACCEPTED', 'SUCCEEDED', 'NO_COLLECTION', 'UNKNOWN', 'CLOSED_WITH_FUNDS_RETURNED')) OR
            (kind = 'REFUND' AND refund_id IS NOT NULL
                AND observed_channel_transaction_no IS NOT NULL AND
             outcome IN ('ACCEPTED', 'SUCCEEDED', 'FAILED_WITH_NO_REFUND', 'UNKNOWN')))),
    CHECK (succeeded_epoch_micros IS NULL OR succeeded_epoch_micros > 0),
    CHECK (accepted_epoch_micros IS NULL OR (accepted_epoch_micros > 0 AND (succeeded_epoch_micros IS NULL OR
                                                                            accepted_epoch_micros <=
                                                                            succeeded_epoch_micros))),
    CHECK (deleted IN (0, 1))
);

CREATE UNIQUE INDEX uk_recon_verify_request ON reconciliation_verification_request (tenant_id, caller_service_id, request_key);

CREATE UNIQUE INDEX uk_recon_verify_delivery ON reconciliation_verification_request (tenant_id, delivery_id);

CREATE INDEX ix_recon_verify_difference ON reconciliation_verification_request (tenant_id, difference_id, id);
