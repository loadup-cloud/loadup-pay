-- PostgreSQL initial schema; all historical changes are folded into the table definitions.

CREATE TABLE payment_parent_gate
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    order_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id VARCHAR(64) COLLATE "C"        NOT NULL,
    caller_service_id VARCHAR(64) COLLATE "C"        NOT NULL,
    state             VARCHAR(24) COLLATE "C"        NOT NULL,
    close_epoch       BIGINT                         NOT NULL DEFAULT 0,
    row_version       BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (state IN ('INIT', 'OPEN', 'FENCED')),
    CHECK ((state = 'FENCED' AND close_epoch > 0) OR (state <> 'FENCED' AND close_epoch = 0)),
    CHECK (row_version >= 0)
);

CREATE UNIQUE INDEX uk_parent_gate ON payment_parent_gate (order_id);

CREATE TABLE payment_gate
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    order_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    payment_id        VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id VARCHAR(64) COLLATE "C"        NOT NULL,
    caller_service_id VARCHAR(64) COLLATE "C"        NOT NULL,
    state             VARCHAR(24) COLLATE "C"        NOT NULL,
    fence_epoch       BIGINT                         NOT NULL DEFAULT 0,
    reason_code       VARCHAR(64) COLLATE "C"        NULL,
    row_version       BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (state IN ('INIT', 'OPEN', 'FENCED')),
    CHECK ((state = 'FENCED' AND fence_epoch > 0) OR (state <> 'FENCED' AND fence_epoch = 0)),
    CHECK (row_version >= 0)
);

CREATE UNIQUE INDEX uk_payment_gate ON payment_gate (payment_id);

CREATE INDEX ix_payment_gate_order ON payment_gate (order_id);

CREATE TABLE payment_order
(
    id                    VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at            TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at            TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted               BOOLEAN                        NOT NULL DEFAULT FALSE,
    order_id              VARCHAR(64) COLLATE "C"        NOT NULL,
    allocation_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    admission_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id     VARCHAR(64) COLLATE "C"        NOT NULL,
    amount_minor          BIGINT                         NOT NULL,
    currency              CHAR(3) COLLATE "C"            NOT NULL,
    request_digest        CHAR(64) COLLATE "C"           NOT NULL,
    caller_service_id     VARCHAR(64) COLLATE "C"        NOT NULL,
    state                 VARCHAR(24) COLLATE "C"        NOT NULL,
    active_attempt_id     VARCHAR(64) COLLATE "C"        NULL,
    successful_attempt_id VARCHAR(64) COLLATE "C"        NULL,
    limit_operation_id    VARCHAR(64) COLLATE "C"        NOT NULL,
    initiated_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    first_unknown_at      TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    succeeded_at          TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    terminal_evidence_id  VARCHAR(64) COLLATE "C"        NULL,
    intent_payload        TEXT                           NOT NULL,
    payload_hash          CHAR(64) COLLATE "C"           NOT NULL,
    row_version           BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (amount_minor > 0 AND currency = 'CNY'),
    CHECK (state IN ('INIT', 'PROCESSING', 'UNKNOWN', 'CLOSING', 'SUCCEEDED', 'FAILED', 'CLOSED')),
    CHECK (state <> 'UNKNOWN' OR first_unknown_at IS NOT NULL),
    CHECK ((state = 'SUCCEEDED' AND successful_attempt_id IS NOT NULL AND active_attempt_id IS NOT NULL AND
            active_attempt_id = successful_attempt_id)
        OR (state <> 'SUCCEEDED' AND successful_attempt_id IS NULL)),
    CHECK (state <> 'INIT' OR (active_attempt_id IS NULL AND successful_attempt_id IS NULL)),
    CHECK (row_version >= 0),
    CONSTRAINT ck_payment_no_collection CHECK (
        state NOT IN ('CLOSED', 'FAILED') OR terminal_evidence_id IS NOT NULL),
    CONSTRAINT ck_payment_success_fact
        CHECK ((state = 'SUCCEEDED' AND succeeded_at IS NOT NULL AND terminal_evidence_id IS NOT NULL)
            OR (state <> 'SUCCEEDED' AND succeeded_at IS NULL))
);

CREATE UNIQUE INDEX uk_payment_allocation ON payment_order (allocation_id);

CREATE UNIQUE INDEX uk_payment_limit_op ON payment_order (limit_operation_id);

CREATE INDEX ix_payment_order ON payment_order (order_id, id);

CREATE INDEX ix_payment_payee_page ON payment_order (payee_merchant_id, created_at, id);

CREATE TABLE payment_control_receipt
(
    id                        VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at                TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at                TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                   BOOLEAN                        NOT NULL DEFAULT FALSE,
    kind                      VARCHAR(24) COLLATE "C"        NOT NULL,
    target_id                 VARCHAR(64) COLLATE "C"        NOT NULL,
    order_id                  VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    caller_service_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    request_key               VARCHAR(128) COLLATE "C"       NOT NULL,
    request_digest            CHAR(64) COLLATE "C"           NOT NULL,
    requested_epoch           BIGINT                         NOT NULL,
    reason_code               VARCHAR(64) COLLATE "C"        NULL,
    no_collection_evidence_id VARCHAR(64) COLLATE "C"        NULL,
    PRIMARY KEY (id),
    CHECK (kind IN ('PARENT_FENCE', 'PAYMENT_CLOSE') AND requested_epoch > 0),
    CHECK ((kind = 'PARENT_FENCE' AND target_id = order_id AND reason_code IS NULL AND
            no_collection_evidence_id IS NULL)
        OR (kind = 'PAYMENT_CLOSE' AND reason_code IS NOT NULL))
);

CREATE UNIQUE INDEX uk_control_request ON payment_control_receipt (kind, target_id, request_key);

CREATE INDEX ix_control_proof ON payment_control_receipt (target_id, kind, no_collection_evidence_id);

CREATE TABLE payment_input
(
    id                 VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted            BOOLEAN                        NOT NULL DEFAULT FALSE,
    payment_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    order_id           VARCHAR(64) COLLATE "C"        NOT NULL,
    allocation_id      VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id  VARCHAR(64) COLLATE "C"        NOT NULL,
    amount_minor       BIGINT                         NOT NULL,
    currency           CHAR(3) COLLATE "C"            NOT NULL,
    input_key          VARCHAR(128) COLLATE "C"       NOT NULL,
    channel            VARCHAR(24) COLLATE "C"        NOT NULL,
    method             VARCHAR(24) COLLATE "C"        NOT NULL,
    caller_service_id  VARCHAR(64) COLLATE "C"        NOT NULL,
    fingerprint_key_id VARCHAR(64) COLLATE "C"        NOT NULL,
    input_digest       CHAR(64) COLLATE "C"           NOT NULL,
    encryption_key     VARCHAR(128) COLLATE "C"       NOT NULL,
    encryption_version INT                            NOT NULL,
    ciphertext         TEXT                           NOT NULL,
    prepared_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    expires_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    state              VARCHAR(24) COLLATE "C"        NOT NULL,
    bound_attempt_id   VARCHAR(64) COLLATE "C"        NULL,
    row_version        BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (amount_minor > 0 AND currency = 'CNY'),
    CHECK (method IN ('PAYMENT_CODE', 'JSAPI', 'H5')),
    CHECK (expires_at > prepared_at AND encryption_version > 0),
    CHECK ((state = 'INIT' AND bound_attempt_id IS NULL AND row_version = 0)
        OR (state = 'BOUND' AND bound_attempt_id IS NOT NULL AND row_version = 1))
);

CREATE UNIQUE INDEX uk_input_payment ON payment_input (payment_id);

CREATE INDEX ix_input_expiry ON payment_input (expires_at, id);

CREATE TABLE channel_merchant_binding
(
    id                  VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted             BOOLEAN                        NOT NULL DEFAULT FALSE,
    payee_merchant_id   VARCHAR(64) COLLATE "C"        NOT NULL,
    binding_code        VARCHAR(64) COLLATE "C"        NOT NULL,
    channel             VARCHAR(24) COLLATE "C"        NOT NULL,
    access_mode         VARCHAR(24) COLLATE "C"        NOT NULL,
    state               VARCHAR(24) COLLATE "C"        NOT NULL,
    current_revision_id VARCHAR(64) COLLATE "C"        NOT NULL,
    current_revision    INT                            NOT NULL,
    priority            INT                            NOT NULL,
    row_version         BIGINT                         NOT NULL,
    CHECK (channel IN ('ALIPAY', 'WECHAT')),
    CHECK (access_mode IN ('DIRECT', 'SERVICE_PROVIDER')),
    CHECK (state IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    CHECK (current_revision > 0 AND row_version > 0 AND priority BETWEEN 0 AND 1000000)
);

CREATE UNIQUE INDEX uk_binding_code ON channel_merchant_binding (payee_merchant_id, binding_code);

CREATE INDEX ix_binding_route ON channel_merchant_binding (payee_merchant_id, channel, access_mode, state, id);

CREATE TABLE channel_binding_revision
(
    id                   VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at           TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at           TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted              BOOLEAN                        NOT NULL DEFAULT FALSE,
    binding_id           VARCHAR(64) COLLATE "C"        NOT NULL,
    revision             INT                            NOT NULL,
    payee_merchant_id    VARCHAR(64) COLLATE "C"        NOT NULL,
    channel              VARCHAR(24) COLLATE "C"        NOT NULL,
    access_mode          VARCHAR(24) COLLATE "C"        NOT NULL,
    account_scope_hash   CHAR(64) COLLATE "C"           NOT NULL,
    credential_reference VARCHAR(128) COLLATE "C"       NOT NULL,
    credential_version   VARCHAR(64) COLLATE "C"        NOT NULL,
    identity_payload     TEXT                           NOT NULL,
    identity_hash        CHAR(64) COLLATE "C"           NOT NULL,
    payment_code_enabled BOOLEAN                        NOT NULL,
    jsapi_enabled        BOOLEAN                        NOT NULL,
    h5_enabled           BOOLEAN                        NOT NULL,
    caller_service_id    VARCHAR(64) COLLATE "C"        NOT NULL,
    published_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (revision > 0 AND channel IN ('ALIPAY', 'WECHAT')),
    CHECK (access_mode IN ('DIRECT', 'SERVICE_PROVIDER')),
    CHECK (payment_code_enabled OR jsapi_enabled OR h5_enabled)
);

CREATE UNIQUE INDEX uk_binding_revision ON channel_binding_revision (binding_id, revision);

CREATE TABLE channel_binding_change_receipt
(
    id                  VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted             BOOLEAN                        NOT NULL DEFAULT FALSE,
    binding_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    kind                VARCHAR(24) COLLATE "C"        NOT NULL,
    request_key         VARCHAR(128) COLLATE "C"       NOT NULL,
    request_digest      CHAR(64) COLLATE "C"           NOT NULL,
    caller_service_id   VARCHAR(64) COLLATE "C"        NOT NULL,
    binding_revision_id VARCHAR(64) COLLATE "C"        NOT NULL,
    revision            INT                            NOT NULL,
    state               VARCHAR(24) COLLATE "C"        NOT NULL,
    applied_version     BIGINT                         NOT NULL,
    applied_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK ((kind = 'PUBLISH' AND state = 'ACTIVE') OR (kind = 'SUSPEND' AND state = 'SUSPENDED')
        OR (kind = 'REVOKE' AND state = 'REVOKED')),
    CHECK (revision > 0 AND applied_version > 0)
);

CREATE UNIQUE INDEX uk_binding_change ON channel_binding_change_receipt (binding_id, request_key);

CREATE TABLE payment_attempt
(
    id                        VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at                TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at                TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                   BOOLEAN                        NOT NULL DEFAULT FALSE,
    payment_id                VARCHAR(64) COLLATE "C"        NOT NULL,
    attempt_no                INT                            NOT NULL,
    amount_minor              BIGINT                         NOT NULL,
    currency                  CHAR(3) COLLATE "C"            NOT NULL,
    binding_revision_id       VARCHAR(64) COLLATE "C"        NOT NULL,
    channel                   VARCHAR(24) COLLATE "C"        NOT NULL,
    access_mode               VARCHAR(24) COLLATE "C"        NOT NULL,
    account_scope_hash        CHAR(64) COLLATE "C"           NOT NULL,
    method                    VARCHAR(24) COLLATE "C"        NOT NULL,
    channel_request_no        VARCHAR(32) COLLATE "C"        NOT NULL,
    state                     VARCHAR(24) COLLATE "C"        NOT NULL,
    execution_epoch           BIGINT                         NOT NULL DEFAULT 0,
    no_collection_evidence_id VARCHAR(64) COLLATE "C"        NULL,
    limit_operation_id        VARCHAR(64) COLLATE "C"        NOT NULL,
    caller_service_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    request_key               VARCHAR(128) COLLATE "C"       NOT NULL,
    request_digest            CHAR(64) COLLATE "C"           NOT NULL,
    payment_request_digest    CHAR(64) COLLATE "C"           NOT NULL,
    prepared_input_ref        VARCHAR(64) COLLATE "C"        NULL,
    risk_decision             VARCHAR(24) COLLATE "C"        NULL,
    risk_reason               VARCHAR(64) COLLATE "C"        NULL,
    risk_consultation_ref     VARCHAR(128) COLLATE "C"       NULL,
    execution_permit_hash     CHAR(64) COLLATE "C"           NULL,
    prepared_at               TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    submission_started_at     TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    row_version               BIGINT                         NOT NULL DEFAULT 0,
    CHECK (attempt_no > 0 AND amount_minor > 0 AND currency = 'CNY' AND row_version >= 0),
    CHECK (method IN ('PAYMENT_CODE', 'JSAPI', 'H5')),
    CHECK (state IN ('INIT', 'SUBMITTING', 'PENDING', 'UNKNOWN', 'SUCCEEDED', 'FAILED', 'CLOSED')),
    CHECK ((execution_epoch = 0 AND execution_permit_hash IS NULL AND submission_started_at IS NULL AND
            state IN ('INIT', 'CLOSED'))
        OR (execution_epoch > 0 AND execution_permit_hash IS NOT NULL AND submission_started_at IS NOT NULL
            AND submission_started_at >= prepared_at AND risk_decision IN ('DEFAULT_ALLOW', 'ALLOW') AND
            risk_reason IS NOT NULL)),
    CHECK (state <> 'INIT' OR
           (risk_decision IS NULL AND risk_reason IS NULL AND risk_consultation_ref IS NULL AND row_version = 0)),
    CHECK (state NOT IN ('FAILED', 'CLOSED') OR no_collection_evidence_id IS NOT NULL),
    CHECK (state <> 'CLOSED' OR row_version > 0),
    CHECK ((risk_decision IS NULL AND risk_reason IS NULL AND risk_consultation_ref IS NULL)
        OR (risk_decision IN ('DEFAULT_ALLOW', 'ALLOW', 'DENY') AND risk_reason IS NOT NULL))
);

CREATE UNIQUE INDEX uk_attempt_no ON payment_attempt (payment_id, attempt_no);

CREATE UNIQUE INDEX uk_attempt_action ON payment_attempt (payment_id, request_key);

CREATE UNIQUE INDEX uk_attempt_channel_request ON payment_attempt (channel, account_scope_hash, channel_request_no);

CREATE INDEX ix_attempt_recovery ON payment_attempt (state, updated_at, id);

CREATE TABLE routing_profile
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    profile_ref       VARCHAR(128) COLLATE "C"       NOT NULL,
    payee_merchant_id VARCHAR(64) COLLATE "C"        NOT NULL,
    spec_payload      TEXT                           NOT NULL,
    content_hash      CHAR(64) COLLATE "C"           NOT NULL,
    caller_service_id VARCHAR(64) COLLATE "C"        NOT NULL,
    request_key       VARCHAR(128) COLLATE "C"       NOT NULL,
    request_digest    CHAR(64) COLLATE "C"           NOT NULL,
    published_at      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (NOT deleted),
    CHECK (spec_payload::json IS NOT NULL),
    CHECK (CHAR_LENGTH(spec_payload) <= 65536)
);

CREATE UNIQUE INDEX uk_routing_profile_ref ON routing_profile (profile_ref);

CREATE UNIQUE INDEX uk_routing_profile_request ON routing_profile (caller_service_id, request_key);

CREATE INDEX ix_routing_profile_payee ON routing_profile (payee_merchant_id, published_at, id);

CREATE TABLE payment_channel_receipt
(
    id                  VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted             BOOLEAN                        NOT NULL DEFAULT FALSE,
    payment_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    attempt_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    channel             VARCHAR(24) COLLATE "C"        NOT NULL,
    binding_revision_id VARCHAR(64) COLLATE "C"        NOT NULL,
    account_scope_hash  CHAR(64) COLLATE "C"           NOT NULL,
    channel_request_no  VARCHAR(64) COLLATE "C"        NOT NULL,
    source              VARCHAR(24) COLLATE "C"        NOT NULL,
    receipt_key         VARCHAR(128) COLLATE "C"       NOT NULL,
    facts_digest        CHAR(64) COLLATE "C"           NOT NULL,
    source_digest       CHAR(64) COLLATE "C"           NOT NULL,
    observation_payload TEXT                           NOT NULL,
    disposition         VARCHAR(24) COLLATE "C"        NOT NULL,
    reason_code         VARCHAR(64) COLLATE "C"        NULL,
    received_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (NOT deleted),
    CHECK (source IN ('SUBMIT_RESPONSE', 'QUERY_RESPONSE', 'CLOSE_RESPONSE', 'CALLBACK')),
    CHECK (disposition IN ('APPLIED', 'IGNORED', 'CONFLICT')),
    CHECK (disposition <> 'CONFLICT' OR reason_code IS NOT NULL),
    CHECK (observation_payload::json IS NOT NULL AND CHAR_LENGTH(observation_payload) <= 32768)
);

CREATE UNIQUE INDEX uk_payment_channel_receipt ON payment_channel_receipt (attempt_id, source, receipt_key);

CREATE INDEX ix_payment_receipt ON payment_channel_receipt (payment_id, received_at, id);

CREATE TABLE payment_result_anomaly
(
    id                  VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted             BOOLEAN                        NOT NULL DEFAULT FALSE,
    payment_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    attempt_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    receipt_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    reason_code         VARCHAR(64) COLLATE "C"        NOT NULL,
    facts_digest        CHAR(64) COLLATE "C"           NOT NULL,
    observation_payload TEXT                           NOT NULL,
    received_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (NOT deleted),
    CHECK (observation_payload::json IS NOT NULL AND CHAR_LENGTH(observation_payload) <= 32768)
);

CREATE UNIQUE INDEX uk_result_anomaly ON payment_result_anomaly (receipt_id, facts_digest, reason_code);

CREATE INDEX ix_result_anomaly_payment ON payment_result_anomaly (payment_id, received_at, id);

CREATE TABLE channel_transaction_claim
(
    id                     VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                BOOLEAN                        NOT NULL DEFAULT FALSE,
    payment_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    attempt_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    channel                VARCHAR(24) COLLATE "C"        NOT NULL,
    binding_revision_id    VARCHAR(64) COLLATE "C"        NOT NULL,
    account_scope_hash     CHAR(64) COLLATE "C"           NOT NULL,
    channel_request_no     VARCHAR(64) COLLATE "C"        NOT NULL,
    amount_minor           BIGINT                         NOT NULL,
    currency               CHAR(3) COLLATE "C"            NOT NULL,
    channel_transaction_no VARCHAR(128) COLLATE "C"       NOT NULL,
    succeeded_at           TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    receipt_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    CHECK (NOT deleted AND amount_minor > 0 AND currency = 'CNY')
);

CREATE UNIQUE INDEX uk_channel_transaction ON channel_transaction_claim (channel, account_scope_hash, channel_transaction_no);

CREATE UNIQUE INDEX uk_claim_attempt ON channel_transaction_claim (attempt_id);

CREATE UNIQUE INDEX uk_claim_payment ON channel_transaction_claim (payment_id);

CREATE TABLE payment_refund_capacity
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    payment_id        VARCHAR(64) COLLATE "C"        NOT NULL,
    order_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id VARCHAR(64) COLLATE "C"        NOT NULL,
    succeeded_at      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    succeeded_minor   BIGINT                         NOT NULL,
    currency          CHAR(3) COLLATE "C"            NOT NULL,
    refunded_minor    BIGINT                         NOT NULL DEFAULT 0,
    reserved_minor    BIGINT                         NOT NULL DEFAULT 0,
    row_version       BIGINT                         NOT NULL DEFAULT 0,
    CHECK (NOT deleted AND succeeded_minor > 0 AND currency = 'CNY' AND row_version >= 0),
    CHECK (refunded_minor >= 0 AND reserved_minor >= 0
        AND refunded_minor <= succeeded_minor AND reserved_minor <= succeeded_minor - refunded_minor)
);

CREATE UNIQUE INDEX uk_refund_capacity_payment ON payment_refund_capacity (payment_id);

CREATE INDEX ix_refund_capacity_fifo ON payment_refund_capacity (order_id, succeeded_at, payment_id);

CREATE TABLE refund_request
(
    id                  VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted             BOOLEAN                        NOT NULL DEFAULT FALSE,
    order_id            VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id   VARCHAR(64) COLLATE "C"        NOT NULL,
    merchant_refund_no  VARCHAR(64) COLLATE "C"        NOT NULL,
    reason_code         VARCHAR(64) COLLATE "C"        NOT NULL,
    request_digest      CHAR(64) COLLATE "C"           NOT NULL,
    caller_subject_hash CHAR(64) COLLATE "C"           NOT NULL,
    admission_id        VARCHAR(64) COLLATE "C"        NOT NULL,
    amount_minor        BIGINT                         NOT NULL,
    currency            CHAR(3) COLLATE "C"            NOT NULL,
    allocation_policy   VARCHAR(24) COLLATE "C"        NOT NULL,
    plan_version        INT                            NOT NULL,
    state               VARCHAR(24) COLLATE "C"        NOT NULL,
    succeeded_minor     BIGINT                         NOT NULL DEFAULT 0,
    failed_minor        BIGINT                         NOT NULL DEFAULT 0,
    unknown_count       INT                            NOT NULL DEFAULT 0,
    refund_terms        TEXT                           NOT NULL,
    terms_hash          CHAR(64) COLLATE "C"           NOT NULL,
    planned_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    row_version         BIGINT                         NOT NULL DEFAULT 0,
    CHECK (NOT deleted AND amount_minor > 0 AND currency = 'CNY' AND plan_version = 1 AND row_version >= 0),
    CHECK (allocation_policy = 'FIFO' AND refund_terms::json IS NOT NULL AND OCTET_LENGTH(refund_terms) <= 262144),
    CHECK (state IN ('INIT', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'PARTIAL_SUCCEEDED')),
    CHECK (succeeded_minor >= 0 AND failed_minor >= 0 AND unknown_count >= 0
        AND succeeded_minor <= amount_minor AND failed_minor <= amount_minor - succeeded_minor)
);

CREATE UNIQUE INDEX uk_refund_merchant ON refund_request (payee_merchant_id, merchant_refund_no);

CREATE INDEX ix_refund_request_order ON refund_request (order_id, created_at, id);

CREATE TABLE refund_allocation_plan
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    refund_request_id VARCHAR(64) COLLATE "C"        NOT NULL,
    plan_version      INT                            NOT NULL,
    sequence_no       INT                            NOT NULL,
    payment_id        VARCHAR(64) COLLATE "C"        NOT NULL,
    refund_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    amount_minor      BIGINT                         NOT NULL,
    CHECK (NOT deleted AND plan_version = 1 AND sequence_no > 0 AND amount_minor > 0)
);

CREATE UNIQUE INDEX uk_refund_plan_seq ON refund_allocation_plan (refund_request_id, plan_version, sequence_no);

CREATE UNIQUE INDEX uk_refund_plan_payment ON refund_allocation_plan (refund_request_id, plan_version, payment_id);

CREATE UNIQUE INDEX uk_refund_plan_child ON refund_allocation_plan (refund_id);

CREATE TABLE refund_order
(
    id                              VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at                      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at                      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                         BOOLEAN                        NOT NULL DEFAULT FALSE,
    refund_request_id               VARCHAR(64) COLLATE "C"        NOT NULL,
    payment_id                      VARCHAR(64) COLLATE "C"        NOT NULL,
    original_attempt_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    binding_revision_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    channel                         VARCHAR(24) COLLATE "C"        NOT NULL,
    account_scope_hash              CHAR(64) COLLATE "C"           NOT NULL,
    original_channel_request_no     VARCHAR(64) COLLATE "C"        NOT NULL,
    original_channel_transaction_no VARCHAR(128) COLLATE "C"       NOT NULL,
    original_succeeded_at           TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    submission_started_at           TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    channel_refund_no               VARCHAR(64) COLLATE "C"        NOT NULL,
    channel_refund_transaction_no   VARCHAR(128) COLLATE "C"       NULL,
    amount_minor                    BIGINT                         NOT NULL,
    currency                        CHAR(3) COLLATE "C"            NOT NULL,
    state                           VARCHAR(24) COLLATE "C"        NOT NULL,
    first_unknown_at                TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    terminal_evidence_id            VARCHAR(64) COLLATE "C"        NULL,
    next_query_at                   TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    query_sequence                  BIGINT                         NOT NULL DEFAULT 0,
    succeeded_at                    TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    row_version                     BIGINT                         NOT NULL DEFAULT 0,
    CHECK (deleted = FALSE AND amount_minor > 0 AND currency = 'CNY' AND row_version >= 0 AND query_sequence >= 0),
    CHECK (state IN ('INIT', 'PROCESSING', 'UNKNOWN', 'SUCCEEDED', 'FAILED')),
    CHECK (state <> 'UNKNOWN' OR first_unknown_at IS NOT NULL),
    CHECK ((state = 'INIT' AND submission_started_at IS NULL) OR
           (state <> 'INIT' AND submission_started_at IS NOT NULL)),
    CHECK ((state IN ('SUCCEEDED', 'FAILED') AND terminal_evidence_id IS NOT NULL)
        OR (state IN ('INIT', 'PROCESSING', 'UNKNOWN') AND terminal_evidence_id IS NULL AND succeeded_at IS NULL)),
    CHECK (state <> 'SUCCEEDED' OR (succeeded_at IS NOT NULL AND channel_refund_transaction_no IS NOT NULL))
);

CREATE UNIQUE INDEX uk_refund_channel_no ON refund_order (channel, account_scope_hash, channel_refund_no);

CREATE INDEX ix_refund_child_request ON refund_order (refund_request_id, id);

CREATE INDEX ix_refund_query ON refund_order (state, next_query_at, id);

CREATE INDEX ix_refund_callback_lookup ON refund_order (binding_revision_id, channel_refund_no);

CREATE TABLE refund_channel_receipt
(
    id                  VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted             BOOLEAN                        NOT NULL DEFAULT FALSE,
    refund_id           VARCHAR(64) COLLATE "C"        NOT NULL,
    source              VARCHAR(24) COLLATE "C"        NOT NULL,
    receipt_key         VARCHAR(128) COLLATE "C"       NOT NULL,
    facts_digest        CHAR(64) COLLATE "C"           NOT NULL,
    source_digest       CHAR(64) COLLATE "C"           NOT NULL,
    observation_payload TEXT                           NOT NULL,
    disposition         VARCHAR(24) COLLATE "C"        NOT NULL,
    reason_code         VARCHAR(64) COLLATE "C"        NULL,
    received_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (NOT deleted AND source IN ('SUBMIT_RESPONSE', 'QUERY_RESPONSE', 'CALLBACK')),
    CHECK (observation_payload::json IS NOT NULL AND OCTET_LENGTH(observation_payload) <= 32768),
    CHECK (disposition IN ('APPLIED', 'IGNORED', 'CONFLICT')),
    CHECK (disposition <> 'CONFLICT' OR reason_code IS NOT NULL)
);

CREATE UNIQUE INDEX uk_refund_channel_receipt ON refund_channel_receipt (refund_id, source, receipt_key);

CREATE TABLE refund_result_anomaly
(
    id                  VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted             BOOLEAN                        NOT NULL DEFAULT FALSE,
    refund_id           VARCHAR(64) COLLATE "C"        NOT NULL,
    receipt_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    reason_code         VARCHAR(64) COLLATE "C"        NOT NULL,
    facts_digest        CHAR(64) COLLATE "C"           NOT NULL,
    observation_payload TEXT                           NOT NULL,
    received_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (NOT deleted AND observation_payload::json IS NOT NULL AND OCTET_LENGTH(observation_payload) <= 32768)
);

CREATE UNIQUE INDEX uk_refund_result_anomaly ON refund_result_anomaly (receipt_id, facts_digest, reason_code);

CREATE INDEX ix_refund_anomaly ON refund_result_anomaly (refund_id, received_at, id);

CREATE TABLE refund_transaction_claim
(
    id                              VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at                      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at                      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                         BOOLEAN                        NOT NULL DEFAULT FALSE,
    refund_id                       VARCHAR(64) COLLATE "C"        NOT NULL,
    payment_id                      VARCHAR(64) COLLATE "C"        NOT NULL,
    attempt_id                      VARCHAR(64) COLLATE "C"        NOT NULL,
    channel                         VARCHAR(24) COLLATE "C"        NOT NULL,
    binding_revision_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    account_scope_hash              CHAR(64) COLLATE "C"           NOT NULL,
    channel_request_no              VARCHAR(64) COLLATE "C"        NOT NULL,
    original_channel_transaction_no VARCHAR(128) COLLATE "C"       NOT NULL,
    channel_refund_no               VARCHAR(64) COLLATE "C"        NOT NULL,
    amount_minor                    BIGINT                         NOT NULL,
    currency                        CHAR(3) COLLATE "C"            NOT NULL,
    channel_refund_transaction_no   VARCHAR(128) COLLATE "C"       NOT NULL,
    succeeded_at                    TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    receipt_id                      VARCHAR(64) COLLATE "C"        NOT NULL,
    CHECK (NOT deleted AND amount_minor > 0 AND currency = 'CNY')
);

CREATE UNIQUE INDEX uk_refund_transaction ON refund_transaction_claim (channel, account_scope_hash, channel_refund_transaction_no);

CREATE UNIQUE INDEX uk_refund_claim_child ON refund_transaction_claim (refund_id);

CREATE TABLE order_refund_summary
(
    id                 VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted            BOOLEAN                        NOT NULL DEFAULT FALSE,
    order_id           VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id  VARCHAR(64) COLLATE "C"        NOT NULL,
    order_amount_minor BIGINT                         NOT NULL,
    currency           CHAR(3) COLLATE "C"            NOT NULL,
    refunded_minor     BIGINT                         NOT NULL DEFAULT 0,
    reserved_minor     BIGINT                         NOT NULL DEFAULT 0,
    row_version        BIGINT                         NOT NULL,
    CHECK (NOT deleted AND order_amount_minor > 0 AND currency = 'CNY' AND row_version > 0),
    CHECK (refunded_minor >= 0 AND reserved_minor >= 0 AND refunded_minor <= order_amount_minor
        AND reserved_minor <= order_amount_minor - refunded_minor)
);

CREATE UNIQUE INDEX uk_order_refund_summary ON order_refund_summary (order_id);

CREATE TABLE refund_recovery_cursor
(
    id         VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted    BOOLEAN                        NOT NULL DEFAULT FALSE,
    generation BIGINT                         NOT NULL,
    due_at     TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    completed  BOOLEAN                        NOT NULL DEFAULT FALSE,
    CHECK (NOT deleted AND generation >= 0)
);


CREATE TABLE payment_action
(
    id                  VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted             BOOLEAN                        NOT NULL DEFAULT FALSE,
    payment_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    channel             VARCHAR(32) COLLATE "C"        NOT NULL,
    binding_revision_id VARCHAR(64) COLLATE "C"        NOT NULL,
    account_scope_hash  VARCHAR(64) COLLATE "C"        NOT NULL,
    channel_request_no  VARCHAR(64) COLLATE "C"        NOT NULL,
    amount_minor        BIGINT                         NOT NULL,
    currency            VARCHAR(3) COLLATE "C"         NOT NULL,
    execution_epoch     BIGINT                         NOT NULL,
    source              VARCHAR(32) COLLATE "C"        NOT NULL,
    receipt_id          VARCHAR(64) COLLATE "C"        NULL,
    source_digest       VARCHAR(64) COLLATE "C"        NOT NULL,
    issued_at           TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    expires_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    action_kind         VARCHAR(32) COLLATE "C"        NOT NULL,
    action_payload      TEXT                           NOT NULL,
    payload_hash        VARCHAR(64) COLLATE "C"        NOT NULL,
    CHECK ((source = 'CHANNEL_RECEIPT' AND receipt_id IS NOT NULL AND action_kind <> 'ALIPAY_H5')
        OR (source = 'LOCAL_SIGNED' AND receipt_id IS NULL AND action_kind = 'ALIPAY_H5' AND
            source_digest = payload_hash)),
    CHECK (NOT deleted AND amount_minor > 0 AND execution_epoch > 0 AND currency = 'CNY')
);

CREATE INDEX ix_payment_action_owner ON payment_action (payment_id);

CREATE UNIQUE INDEX uk_payment_action_receipt ON payment_action (receipt_id);

CREATE TABLE payment_recovery_cursor
(
    id         VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted    BOOLEAN                        NOT NULL DEFAULT FALSE,
    generation BIGINT                         NOT NULL,
    due_at     TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    completed  BOOLEAN                        NOT NULL DEFAULT FALSE,
    CHECK (NOT deleted AND generation >= 0)
);


CREATE TABLE payment_recovery_retry_receipt
(
    id                       VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at               TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at               TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                  BOOLEAN                        NOT NULL DEFAULT FALSE,
    request_key              VARCHAR(128) COLLATE "C"       NOT NULL,
    caller_scope_hash        CHAR(64) COLLATE "C"           NOT NULL,
    caller_service_id        VARCHAR(64) COLLATE "C"        NOT NULL,
    request_digest           CHAR(64) COLLATE "C"           NOT NULL,
    kind                     VARCHAR(32) COLLATE "C"        NOT NULL,
    subject_id               VARCHAR(64) COLLATE "C"        NOT NULL,
    generation               BIGINT                         NOT NULL,
    order_id                 VARCHAR(64) COLLATE "C"        NOT NULL,
    native_task_id           VARCHAR(64) COLLATE "C"        NOT NULL,
    expected_attempts        INT                            NOT NULL,
    expected_next_execute_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    reason                   VARCHAR(500) COLLATE "C"       NOT NULL,
    result                   VARCHAR(32) COLLATE "C"        NOT NULL,
    accepted_at              TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (NOT deleted AND generation >= 0 AND expected_attempts > 0),
    CHECK (kind IN ('COLLECTION', 'REFUND_REQUEST')),
    CHECK (result IN ('RETRIED', 'NO_LONGER_NEEDED'))
);

CREATE UNIQUE INDEX uk_payment_recovery_retry ON payment_recovery_retry_receipt (caller_scope_hash, request_key);

CREATE INDEX ix_payment_recovery_retry_order ON payment_recovery_retry_receipt (order_id, accepted_at);

CREATE TABLE refund_acceptance_evidence
(
    id                              VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at                      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at                      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                         BOOLEAN                        NOT NULL DEFAULT FALSE,
    payment_id                      VARCHAR(64) COLLATE "C"        NOT NULL,
    attempt_id                      VARCHAR(64) COLLATE "C"        NOT NULL,
    channel                         VARCHAR(32) COLLATE "C"        NOT NULL,
    binding_revision_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    account_scope_hash              VARCHAR(64) COLLATE "C"        NOT NULL,
    channel_request_no              VARCHAR(64) COLLATE "C"        NOT NULL,
    original_channel_transaction_no VARCHAR(128) COLLATE "C"       NOT NULL,
    channel_refund_no               VARCHAR(64) COLLATE "C"        NOT NULL,
    amount_minor                    BIGINT                         NOT NULL,
    currency                        VARCHAR(3) COLLATE "C"         NOT NULL,
    accepted_at                     TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    receipt_id                      VARCHAR(64) COLLATE "C"        NOT NULL,
    CHECK (NOT deleted AND amount_minor > 0 AND currency = 'CNY')
);

CREATE TABLE payment_reconciliation_capture
(
    id                   VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at           TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at           TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted              BOOLEAN                        NOT NULL DEFAULT FALSE,
    caller_service_id    VARCHAR(64) COLLATE "C"        NOT NULL,
    request_key          VARCHAR(128) COLLATE "C"       NOT NULL,
    request_digest       VARCHAR(64) COLLATE "C"        NOT NULL,
    channel              VARCHAR(32) COLLATE "C"        NOT NULL,
    account_scope_hash   VARCHAR(64) COLLATE "C"        NOT NULL,
    bill_date            DATE                           NOT NULL,
    statement_profile    VARCHAR(64) COLLATE "C"        NOT NULL,
    system_zone          VARCHAR(64) COLLATE "C"        NOT NULL,
    state                VARCHAR(16) COLLATE "C"        NOT NULL,
    started_at           TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    finished_at          TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    payment_count        BIGINT                         NULL,
    payment_amount_minor BIGINT                         NULL,
    refund_count         BIGINT                         NULL,
    refund_amount_minor  BIGINT                         NULL,
    content_hash         VARCHAR(64) COLLATE "C"        NULL,
    blocker              VARCHAR(64) COLLATE "C"        NULL,
    blocker_subject_id   VARCHAR(64) COLLATE "C"        NULL,
    row_version          BIGINT                         NOT NULL,
    CHECK (NOT deleted AND channel = 'WECHAT' AND statement_profile = 'wechat-trade-all-20260116' AND
           row_version = CASE WHEN state = 'INIT' THEN 0 ELSE 1 END),
    CHECK ((state = 'INIT' AND started_at IS NULL AND finished_at IS NULL)
        OR (state IN ('SEALED', 'BLOCKED') AND started_at IS NOT NULL AND finished_at IS NOT NULL AND
            finished_at >= started_at)),
    CHECK ((state = 'SEALED' AND payment_count IS NOT NULL AND payment_amount_minor IS NOT NULL AND
            refund_count IS NOT NULL
        AND refund_amount_minor IS NOT NULL AND content_hash IS NOT NULL AND payment_count >= 0 AND refund_count >= 0
        AND payment_amount_minor >= payment_count AND refund_amount_minor >= refund_count
        AND ((payment_count = 0 AND payment_amount_minor = 0) OR (payment_count > 0 AND payment_amount_minor > 0))
        AND ((refund_count = 0 AND refund_amount_minor = 0) OR (refund_count > 0 AND refund_amount_minor > 0)))
        OR (state IN ('INIT', 'BLOCKED') AND payment_count IS NULL AND payment_amount_minor IS NULL AND
            refund_count IS NULL AND refund_amount_minor IS NULL AND content_hash IS NULL)),
    CHECK ((state = 'BLOCKED' AND blocker IS NOT NULL AND blocker_subject_id IS NOT NULL)
        OR (state IN ('INIT', 'SEALED') AND blocker IS NULL AND blocker_subject_id IS NULL))
);

CREATE UNIQUE INDEX uk_payment_capture_request ON payment_reconciliation_capture (caller_service_id, request_key);

CREATE TABLE payment_reconciliation_line
(
    id                     VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                BOOLEAN                        NOT NULL DEFAULT FALSE,
    capture_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    ordinal                BIGINT                         NOT NULL,
    key_hash               VARCHAR(64) COLLATE "C"        NOT NULL,
    kind                   VARCHAR(16) COLLATE "C"        NOT NULL,
    channel_transaction_no VARCHAR(128) COLLATE "C"       NOT NULL,
    channel_refund_no      VARCHAR(64) COLLATE "C"        NULL,
    merchant_payment_no    VARCHAR(64) COLLATE "C"        NOT NULL,
    amount_minor           BIGINT                         NOT NULL,
    currency               VARCHAR(3) COLLATE "C"         NOT NULL,
    status                 VARCHAR(16) COLLATE "C"        NOT NULL,
    source_at              TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    source_id              VARCHAR(64) COLLATE "C"        NOT NULL,
    evidence_id            VARCHAR(64) COLLATE "C"        NOT NULL,
    source_version         BIGINT                         NOT NULL,
    CHECK (NOT deleted AND ordinal > 0 AND amount_minor > 0 AND currency = 'CNY' AND source_version >= 0),
    CHECK ((kind = 'PAYMENT' AND channel_refund_no IS NULL AND status = 'SUCCEEDED')
        OR (kind = 'REFUND' AND channel_refund_no IS NOT NULL AND
            status IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'UNKNOWN')))
);

CREATE UNIQUE INDEX uk_payment_capture_ordinal ON payment_reconciliation_line (capture_id, ordinal);

CREATE UNIQUE INDEX uk_payment_capture_key ON payment_reconciliation_line (capture_id, key_hash);

CREATE TABLE payment_close_evidence
(
    id                     VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                BOOLEAN                        NOT NULL DEFAULT FALSE,
    payment_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    attempt_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    channel                VARCHAR(32) COLLATE "C"        NOT NULL,
    binding_revision_id    VARCHAR(64) COLLATE "C"        NOT NULL,
    account_scope_hash     VARCHAR(64) COLLATE "C"        NOT NULL,
    channel_request_no     VARCHAR(64) COLLATE "C"        NOT NULL,
    amount_minor           BIGINT                         NOT NULL,
    currency               VARCHAR(3) COLLATE "C"         NOT NULL,
    channel_transaction_no VARCHAR(128) COLLATE "C",
    receipt_key            VARCHAR(128) COLLATE "C"       NOT NULL,
    source_digest          VARCHAR(64) COLLATE "C"        NOT NULL,
    execution_epoch        BIGINT                         NOT NULL,
    recorded_at            TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (id = attempt_id AND NOT deleted AND amount_minor > 0 AND currency = 'CNY' AND execution_epoch > 0)
);

CREATE INDEX ix_attempt_callback_request
    ON payment_attempt (binding_revision_id, channel_request_no);

CREATE INDEX ix_refund_acceptance_window ON refund_acceptance_evidence (channel, account_scope_hash, accepted_at, id);

CREATE INDEX ix_payment_attempt_recon_lookup ON payment_attempt (channel, account_scope_hash, channel_request_no);

CREATE INDEX ix_refund_order_recon_lookup ON refund_order (channel, account_scope_hash,
                                                           original_channel_request_no, channel_refund_no);
