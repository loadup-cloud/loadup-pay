-- Frozen approved consultation and immutable owner query observations; no financial postings.
CREATE TABLE reconciliation_verification_request
(
    id                              VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_id                       VARCHAR(64)  NOT NULL,
    created_at                      DATETIME(6)  NOT NULL,
    updated_at                      DATETIME(6)  NOT NULL,
    deleted                         TINYINT      NOT NULL DEFAULT 0,
    difference_id                   VARCHAR(64)  NOT NULL,
    batch_id                        VARCHAR(64)  NOT NULL,
    run_id                          VARCHAR(64)  NOT NULL,
    result_hash                     VARCHAR(64)  NOT NULL,
    match_key_hash                  VARCHAR(64)  NOT NULL,
    proposal_id                     VARCHAR(64)  NOT NULL,
    approval_event_id               VARCHAR(64)  NOT NULL,
    review_version                  BIGINT       NOT NULL,
    caller_service_id               VARCHAR(64)  NOT NULL,
    request_key                     VARCHAR(128) NOT NULL,
    request_digest                  VARCHAR(64)  NOT NULL,
    channel                         VARCHAR(64)  NOT NULL,
    account_scope_hash              VARCHAR(64)  NOT NULL,
    bill_date                       DATE         NOT NULL,
    statement_profile               VARCHAR(64)  NOT NULL,
    kind                            VARCHAR(64)  NOT NULL,
    channel_transaction_no          VARCHAR(128) NOT NULL,
    channel_refund_no               VARCHAR(64)  NULL,
    merchant_payment_no             VARCHAR(64)  NOT NULL,
    delivery_id                     VARCHAR(64)  NOT NULL,
    state                           VARCHAR(64)  NOT NULL,
    row_version                     BIGINT       NOT NULL,
    disposition                     VARCHAR(64)  NULL,
    binding_revision_id             VARCHAR(64)  NULL,
    payment_id                      VARCHAR(64)  NULL,
    attempt_id                      VARCHAR(64)  NULL,
    refund_id                       VARCHAR(64)  NULL,
    source_version                  BIGINT       NULL,
    local_state                     VARCHAR(64)  NULL,
    outcome                         VARCHAR(64)  NULL,
    amount_minor                    BIGINT       NULL,
    currency                        VARCHAR(64)  NULL,
    observed_channel_transaction_no VARCHAR(128) NULL,
    observed_refund_transaction_no  VARCHAR(128) NULL,
    succeeded_epoch_micros          BIGINT       NULL,
    source_digest                   VARCHAR(64)  NULL,
    started_epoch_micros            BIGINT       NULL,
    finished_epoch_micros           BIGINT       NULL,
    accepted_epoch_micros           BIGINT       NULL,
    UNIQUE KEY uk_recon_verify_request (tenant_id, caller_service_id, request_key),
    UNIQUE KEY uk_recon_verify_delivery (tenant_id, delivery_id),
    KEY ix_recon_verify_difference (tenant_id, difference_id, id),
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
                                                                            succeeded_epoch_micros)))

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
