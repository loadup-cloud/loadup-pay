-- Independent administrative review; original differences/results remain immutable.
CREATE TABLE reconciliation_review
(
    id             VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id      VARCHAR(64) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    deleted        TINYINT     NOT NULL DEFAULT 0,
    batch_id       VARCHAR(64) NOT NULL,
    run_id         VARCHAR(64) NOT NULL,
    result_hash    VARCHAR(64) NOT NULL,
    match_key_hash VARCHAR(64) NOT NULL,
    state          VARCHAR(32) NOT NULL,
    proposal_id    VARCHAR(64) NOT NULL,
    last_event_id  VARCHAR(64) NOT NULL,
    row_version    BIGINT      NOT NULL,
    CHECK (deleted = 0),
    UNIQUE KEY uk_recon_review_last_event (tenant_id, last_event_id),
    CHECK ((state = 'PROPOSED' AND row_version > 0 AND MOD(row_version, 2) = 1) OR
           (state IN ('APPROVED', 'REJECTED') AND row_version >= 2 AND MOD(row_version, 2) = 0))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE reconciliation_review_proposal
(
    id                  VARCHAR(64)   NOT NULL PRIMARY KEY,
    tenant_id           VARCHAR(64)   NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    deleted             TINYINT       NOT NULL DEFAULT 0,
    difference_id       VARCHAR(64)   NOT NULL,
    batch_id            VARCHAR(64)   NOT NULL,
    run_id              VARCHAR(64)   NOT NULL,
    result_hash         VARCHAR(64)   NOT NULL,
    match_key_hash      VARCHAR(64)   NOT NULL,
    proposer_service_id VARCHAR(64)   NOT NULL,
    conclusion          VARCHAR(64)   NOT NULL,
    note                VARCHAR(1024) NULL,
    evidence_file_id    VARCHAR(64)   NULL,
    evidence_storage_id VARCHAR(1024) NULL,
    evidence_size       BIGINT        NULL,
    evidence_sha256     VARCHAR(64)   NULL,
    proposed_at         DATETIME(6)   NOT NULL,
    CHECK (deleted = 0),
    KEY ix_recon_review_proposal (tenant_id, difference_id, id),
    CHECK (conclusion IN ('CONFIRMED_DIFFERENCE', 'TIMING_DIFFERENCE', 'INPUT_DEFECT', 'NEEDS_SOURCE_VERIFICATION') AND
           note IS NOT NULL),
    CHECK ((evidence_file_id IS NULL AND evidence_storage_id IS NULL AND evidence_size IS NULL AND
            evidence_sha256 IS NULL AND conclusion <> 'INPUT_DEFECT') OR
           (evidence_file_id IS NOT NULL AND evidence_storage_id IS NOT NULL AND evidence_size IS NOT NULL AND
            evidence_size > 0 AND evidence_sha256 IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE reconciliation_review_event
(
    id                VARCHAR(64)   NOT NULL PRIMARY KEY,
    tenant_id         VARCHAR(64)   NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    difference_id     VARCHAR(64)   NOT NULL,
    batch_id          VARCHAR(64)   NOT NULL,
    run_id            VARCHAR(64)   NOT NULL,
    result_hash       VARCHAR(64)   NOT NULL,
    match_key_hash    VARCHAR(64)   NOT NULL,
    caller_service_id VARCHAR(64)   NOT NULL,
    request_key       VARCHAR(128)  NOT NULL,
    request_digest    VARCHAR(64)   NOT NULL,
    operation         VARCHAR(32)   NOT NULL,
    proposal_id       VARCHAR(64)   NOT NULL,
    state             VARCHAR(32)   NOT NULL,
    source_version    BIGINT        NOT NULL,
    note              VARCHAR(1024) NULL,
    occurred_at       DATETIME(6)   NOT NULL,
    CHECK (deleted = 0),
    UNIQUE KEY uk_recon_review_command (tenant_id, caller_service_id, request_key),
    UNIQUE KEY uk_recon_review_sequence (tenant_id, difference_id, source_version),
    CHECK ((operation = 'PROPOSE' AND state = 'PROPOSED' AND source_version > 0 AND MOD(source_version, 2) = 1 AND
            note IS NULL) OR (operation IN ('APPROVE', 'REJECT') AND
                              state = CASE WHEN operation = 'APPROVE' THEN 'APPROVED' ELSE 'REJECTED' END AND
                              source_version >= 2 AND MOD(source_version, 2) = 0 AND note IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

