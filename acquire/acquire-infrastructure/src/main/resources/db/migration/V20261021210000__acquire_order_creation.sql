-- PostgreSQL initial schema; all historical changes are folded into the table definitions.

CREATE TABLE order_create_intent
(
    id                 VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted            BOOLEAN                        NOT NULL DEFAULT FALSE,
    payee_merchant_id  VARCHAR(64) COLLATE "C"        NOT NULL,
    merchant_order_no  VARCHAR(64) COLLATE "C"        NOT NULL,
    order_id           VARCHAR(64) COLLATE "C"        NOT NULL,
    request_digest     CHAR(64) COLLATE "C"           NOT NULL,
    caller_scope_hash  CHAR(64) COLLATE "C"           NOT NULL,
    entry_mode         VARCHAR(24) COLLATE "C"        NOT NULL,
    intent_payload     TEXT                           NOT NULL,
    state              VARCHAR(24) COLLATE "C"        NOT NULL,
    admission_id       VARCHAR(64) COLLATE "C"        NULL,
    materialize_before TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    result_code        VARCHAR(64) COLLATE "C"        NULL,
    row_version        BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (row_version >= 0),
    CHECK ((state = 'INIT' AND admission_id IS NULL AND materialize_before IS NULL AND result_code IS NULL)
        OR (state = 'REJECTED' AND admission_id IS NOT NULL AND result_code IS NOT NULL)
        OR (state IN ('COMPLETED', 'EXPIRED') AND admission_id IS NOT NULL AND materialize_before IS NOT NULL AND
            result_code IS NOT NULL)),
    initial_session_id VARCHAR(64) COLLATE "C"        NULL,
    CONSTRAINT ck_intent_entry_state CHECK (
        entry_mode IN ('ORDER', 'CHECKOUT', 'DIRECT_PAY') AND state IN ('INIT', 'COMPLETED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_intent_initial_session CHECK (
        (entry_mode IN ('ORDER', 'DIRECT_PAY') AND initial_session_id IS NULL)
            OR (entry_mode = 'CHECKOUT' AND initial_session_id IS NOT NULL))
);

CREATE UNIQUE INDEX uk_intent_merchant ON order_create_intent (tenant_id, payee_merchant_id, merchant_order_no);

CREATE UNIQUE INDEX uk_intent_order ON order_create_intent (tenant_id, order_id);

CREATE INDEX ix_intent_recovery ON order_create_intent (tenant_id, state, updated_at);

CREATE UNIQUE INDEX uk_intent_initial_session ON order_create_intent (tenant_id, initial_session_id);

CREATE TABLE acquire_order
(
    id                       VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id                VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at               TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at               TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                  BOOLEAN                        NOT NULL DEFAULT FALSE,
    payee_merchant_id        VARCHAR(64) COLLATE "C"        NOT NULL,
    merchant_order_no        VARCHAR(64) COLLATE "C"        NOT NULL,
    admission_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    contract_snapshot_id     VARCHAR(64) COLLATE "C"        NOT NULL,
    subject                  VARCHAR(200) COLLATE "C"       NOT NULL,
    currency                 CHAR(3) COLLATE "C"            NOT NULL,
    amount_minor             BIGINT                         NOT NULL,
    paid_amount_minor        BIGINT                         NOT NULL DEFAULT 0,
    payment_reserved_minor   BIGINT                         NOT NULL DEFAULT 0,
    successful_payment_count INT                            NOT NULL DEFAULT 0,
    reserved_payment_count   INT                            NOT NULL DEFAULT 0,
    state                    VARCHAR(24) COLLATE "C"        NOT NULL,
    admitted_at              TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    order_due_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deadline_version         BIGINT                         NOT NULL DEFAULT 1,
    close_epoch              BIGINT                         NOT NULL DEFAULT 0,
    paid_at                  TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    closed_at                TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    row_version              BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (amount_minor > 0 AND currency = 'CNY'),
    CHECK (row_version >= 0 AND deadline_version > 0 AND close_epoch >= 0),
    CHECK (paid_amount_minor >= 0 AND payment_reserved_minor >= 0),
    CHECK (paid_amount_minor <= amount_minor AND payment_reserved_minor <= amount_minor - paid_amount_minor),
    CHECK (successful_payment_count >= 0 AND reserved_payment_count >= 0),
    CHECK (state IN ('INIT', 'PENDING', 'CLOSING', 'PAID', 'CLOSED')),
    CHECK (state <> 'PAID' OR (paid_amount_minor = amount_minor AND payment_reserved_minor = 0))
);

CREATE UNIQUE INDEX uk_order_merchant ON acquire_order (tenant_id, payee_merchant_id, merchant_order_no);

CREATE UNIQUE INDEX uk_order_admission ON acquire_order (tenant_id, admission_id);

CREATE INDEX ix_order_payee_page ON acquire_order (tenant_id, payee_merchant_id, created_at, id);

CREATE INDEX ix_order_due ON acquire_order (tenant_id, state, order_due_at, id);

CREATE TABLE order_contract_snapshot
(
    id                  VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id           VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted             BOOLEAN                        NOT NULL DEFAULT FALSE,
    order_id            VARCHAR(64) COLLATE "C"        NOT NULL,
    contract_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    contract_revision   INT                            NOT NULL,
    contract_generation BIGINT                         NOT NULL,
    snapshot_hash       VARCHAR(128) COLLATE "C"       NOT NULL,
    compiled_terms_hash CHAR(64) COLLATE "C"           NOT NULL,
    schema_version      INT                            NOT NULL,
    configuration       TEXT                           NOT NULL,
    payload_hash        CHAR(64) COLLATE "C"           NOT NULL,
    admission_digest    CHAR(64) COLLATE "C"           NOT NULL,
    PRIMARY KEY (id),
    CHECK (contract_revision > 0 AND contract_generation > 0 AND schema_version = 1)
);

CREATE UNIQUE INDEX uk_snapshot_order ON order_contract_snapshot (tenant_id, order_id);

CREATE TABLE order_fee
(
    id                    VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at            TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at            TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted               BOOLEAN                        NOT NULL DEFAULT FALSE,
    order_id              VARCHAR(64) COLLATE "C"        NOT NULL,
    fee_type              VARCHAR(24) COLLATE "C"        NOT NULL,
    currency              CHAR(3) COLLATE "C"            NOT NULL,
    base_amount_minor     BIGINT                         NOT NULL,
    fee_amount_minor      BIGINT                         NOT NULL,
    fee_rule_snapshot     TEXT                           NOT NULL,
    refund_basis_version  BIGINT                         NOT NULL DEFAULT 0,
    refund_basis_minor    BIGINT                         NOT NULL DEFAULT 0,
    adjusted_amount_minor BIGINT                         NOT NULL DEFAULT 0,
    state                 VARCHAR(24) COLLATE "C"        NOT NULL,
    confirmed_at          TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    row_version           BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (base_amount_minor > 0 AND fee_amount_minor >= 0 AND currency = 'CNY'),
    CHECK (fee_type = 'ORDER_AMOUNT' AND state IN ('INIT', 'CONFIRMED')),
    CHECK (refund_basis_version >= 0 AND row_version >= 0),
    CHECK (refund_basis_minor >= 0 AND refund_basis_minor <= base_amount_minor),
    CHECK (adjusted_amount_minor >= 0 AND adjusted_amount_minor <= fee_amount_minor)
);

CREATE UNIQUE INDEX uk_fee_order_type ON order_fee (tenant_id, order_id, fee_type);

CREATE TABLE checkout_session
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    order_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id VARCHAR(64) COLLATE "C"        NOT NULL,
    request_key       VARCHAR(128) COLLATE "C"       NOT NULL,
    request_digest    CHAR(64) COLLATE "C"           NOT NULL,
    token_hash        CHAR(64) COLLATE "C"           NOT NULL,
    token_nonce       CHAR(64) COLLATE "C"           NOT NULL,
    token_key_ref     VARCHAR(128) COLLATE "C"       NOT NULL,
    token_key_version VARCHAR(64) COLLATE "C"        NOT NULL,
    state             VARCHAR(24) COLLATE "C"        NOT NULL,
    expires_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    allowed_origin    VARCHAR(512) COLLATE "C"       NULL,
    scope_snapshot    TEXT                           NOT NULL,
    row_version       BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (state IN ('INIT', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    CHECK (row_version >= 0 AND date_trunc('second', expires_at) = expires_at),
    CHECK (allowed_origin IS NOT NULL),
    revoked_at        TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    CONSTRAINT ck_checkout_revocation_version CHECK (revoked_at IS NULL OR row_version > 0)
);

CREATE UNIQUE INDEX uk_session_request ON checkout_session (tenant_id, order_id, request_key);

CREATE UNIQUE INDEX uk_session_token ON checkout_session (token_hash);

CREATE INDEX ix_session_order ON checkout_session (tenant_id, order_id, state);

CREATE TABLE order_payment_allocation
(
    id                     VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id              VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                BOOLEAN                        NOT NULL DEFAULT FALSE,
    order_id               VARCHAR(64) COLLATE "C"        NOT NULL,
    payment_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    actor_scope            VARCHAR(128) COLLATE "C"       NOT NULL,
    action_key             VARCHAR(128) COLLATE "C"       NOT NULL,
    request_digest         CHAR(64) COLLATE "C"           NOT NULL,
    caller_scope_hash      CHAR(64) COLLATE "C"           NOT NULL,
    amount_minor           BIGINT                         NOT NULL,
    currency               CHAR(3) COLLATE "C"            NOT NULL,
    state                  VARCHAR(24) COLLATE "C"        NOT NULL,
    payment_source_version BIGINT                         NOT NULL DEFAULT 0,
    intent_payload         TEXT                           NOT NULL,
    payload_hash           CHAR(64) COLLATE "C"           NOT NULL,
    row_version            BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (amount_minor > 0 AND currency = 'CNY'),
    CHECK (state IN ('INIT', 'HELD', 'CONSUMED', 'RELEASED')),
    CHECK (payment_source_version >= 0 AND row_version >= 0)
);

CREATE UNIQUE INDEX uk_allocation_payment ON order_payment_allocation (tenant_id, payment_id);

CREATE UNIQUE INDEX uk_allocation_action ON order_payment_allocation (tenant_id, order_id, actor_scope, action_key);

CREATE INDEX ix_allocation_order ON order_payment_allocation (tenant_id, order_id, state, payment_id);

CREATE TABLE order_payment_outcome
(
    id                     VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id              VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                BOOLEAN                        NOT NULL DEFAULT FALSE,
    allocation_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    order_id               VARCHAR(64) COLLATE "C"        NOT NULL,
    payment_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    amount_minor           BIGINT                         NOT NULL,
    currency               CHAR(3) COLLATE "C"            NOT NULL,
    outcome_kind           VARCHAR(24) COLLATE "C"        NOT NULL,
    payment_source_version BIGINT                         NOT NULL,
    evidence_id            VARCHAR(64) COLLATE "C"        NOT NULL,
    execution_fenced       BOOLEAN                        NOT NULL,
    observed_at            TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CHECK (amount_minor > 0 AND currency = 'CNY'),
    CONSTRAINT ck_order_outcome_source CHECK (
        (outcome_kind = 'NOT_ACCEPTED' AND payment_source_version = 0)
            OR (outcome_kind IN ('SUCCEEDED', 'NO_COLLECTION') AND payment_source_version > 0)),
    CONSTRAINT ck_order_outcome_fence CHECK (
        outcome_kind = 'SUCCEEDED' OR execution_fenced = TRUE)
);

CREATE UNIQUE INDEX uk_order_outcome_allocation ON order_payment_outcome (tenant_id, allocation_id);

CREATE UNIQUE INDEX uk_order_outcome_payment ON order_payment_outcome (tenant_id, payment_id);

CREATE INDEX ix_order_outcome_order ON order_payment_outcome (tenant_id, order_id, payment_id);

CREATE TABLE order_close_intent
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    order_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id VARCHAR(64) COLLATE "C"        NOT NULL,
    caller_scope_hash CHAR(64) COLLATE "C"           NOT NULL,
    request_key       VARCHAR(128) COLLATE "C"       NOT NULL,
    request_digest    CHAR(64) COLLATE "C"           NOT NULL,
    reason_code       VARCHAR(64) COLLATE "C"        NOT NULL,
    close_epoch       BIGINT                         NOT NULL,
    requested_at      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    cursor_payment_id VARCHAR(64) COLLATE "C"        NOT NULL DEFAULT '',
    row_version       BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (close_epoch > 0 AND row_version >= 0)
);

CREATE UNIQUE INDEX uk_order_close_order ON order_close_intent (tenant_id, order_id);

CREATE TABLE order_extension_receipt
(
    id                     VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id              VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                BOOLEAN                        NOT NULL DEFAULT FALSE,
    order_id               VARCHAR(64) COLLATE "C"        NOT NULL,
    caller_scope_hash      CHAR(64) COLLATE "C"           NOT NULL,
    request_key            VARCHAR(128) COLLATE "C"       NOT NULL,
    request_digest         CHAR(64) COLLATE "C"           NOT NULL,
    original_due_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    previous_due_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    requested_due_at       TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    expected_order_version BIGINT                         NOT NULL,
    deadline_version       BIGINT                         NOT NULL,
    applied_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CHECK (expected_order_version >= 0 AND deadline_version > 1),
    CHECK (previous_due_at >= original_due_at AND requested_due_at > previous_due_at
        AND requested_due_at > applied_at)
);

CREATE UNIQUE INDEX uk_order_extension_request ON order_extension_receipt (tenant_id, order_id, request_key);

CREATE UNIQUE INDEX uk_order_extension_deadline ON order_extension_receipt (tenant_id, order_id, deadline_version);

CREATE TABLE acquire_payment_event_receipt
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    tenant_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    delivery_id       VARCHAR(64) COLLATE "C"        NOT NULL,
    business_event_id VARCHAR(64) COLLATE "C"        NOT NULL,
    payment_id        VARCHAR(64) COLLATE "C"        NOT NULL,
    order_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    allocation_id     VARCHAR(64) COLLATE "C"        NOT NULL,
    payment_version   BIGINT                         NOT NULL,
    evidence_id       VARCHAR(64) COLLATE "C"        NOT NULL,
    request_digest    CHAR(64) COLLATE "C"           NOT NULL,
    caller_service_id VARCHAR(64) COLLATE "C"        NOT NULL,
    order_version     BIGINT                         NOT NULL,
    accepted_at       TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (deleted = FALSE AND payment_version > 0 AND order_version >= 0)
);

CREATE UNIQUE INDEX uk_acquire_payment_delivery ON acquire_payment_event_receipt (tenant_id, delivery_id);

CREATE INDEX ix_acquire_payment_event ON acquire_payment_event_receipt (tenant_id, payment_id, payment_version, business_event_id);

CREATE TABLE fee_adjustment
(
    id                        VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    tenant_id                 VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at                TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at                TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                   BOOLEAN                        NOT NULL DEFAULT FALSE,
    order_id                  VARCHAR(64) COLLATE "C"        NOT NULL,
    fee_id                    VARCHAR(64) COLLATE "C"        NOT NULL,
    trigger_key               VARCHAR(128) COLLATE "C"       NOT NULL,
    source_event_id           VARCHAR(64) COLLATE "C"        NOT NULL,
    source_version            BIGINT                         NOT NULL,
    adjustment_minor          BIGINT                         NOT NULL,
    currency                  CHAR(3) COLLATE "C"            NOT NULL,
    cumulative_refunded_minor BIGINT                         NOT NULL,
    rule_snapshot             TEXT                           NOT NULL,
    applied_at                TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (deleted = FALSE AND adjustment_minor > 0 AND currency = 'CNY' AND source_version > 0 AND
           cumulative_refunded_minor > 0),
    CHECK (rule_snapshot::json IS NOT NULL AND OCTET_LENGTH(rule_snapshot) <= 32768)
);

CREATE UNIQUE INDEX uk_fee_adjust_trigger ON fee_adjustment (tenant_id, fee_id, trigger_key);

CREATE INDEX ix_fee_adjust_order ON fee_adjustment (tenant_id, order_id, created_at, id);

CREATE TABLE fee_refund_receipt
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    tenant_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    delivery_id       VARCHAR(64) COLLATE "C"        NOT NULL,
    business_event_id VARCHAR(64) COLLATE "C"        NOT NULL,
    order_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    source_version    BIGINT                         NOT NULL,
    applied_version   BIGINT                         NOT NULL,
    request_digest    CHAR(64) COLLATE "C"           NOT NULL,
    caller_service_id VARCHAR(64) COLLATE "C"        NOT NULL,
    accepted_at       TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (deleted = FALSE AND source_version > 0 AND applied_version >= source_version)
);

CREATE UNIQUE INDEX uk_fee_refund_delivery ON fee_refund_receipt (tenant_id, delivery_id);

CREATE INDEX ix_fee_refund_order ON fee_refund_receipt (tenant_id, order_id, applied_version);

CREATE TABLE merchant_notification_delivery
(
    id                    VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at            TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at            TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted               BOOLEAN                        NOT NULL DEFAULT FALSE,
    event_id              VARCHAR(64) COLLATE "C"        NOT NULL,
    order_id              VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id     VARCHAR(64) COLLATE "C"        NOT NULL,
    event_type            VARCHAR(64) COLLATE "C"        NOT NULL,
    aggregate_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    source_version        BIGINT                         NOT NULL,
    body                  TEXT                           NOT NULL,
    body_digest           CHAR(64) COLLATE "C"           NOT NULL,
    target_ref            VARCHAR(128) COLLATE "C"       NOT NULL,
    signing_identity_ref  VARCHAR(128) COLLATE "C"       NOT NULL,
    timeout_millis        INT                            NOT NULL,
    retry_initial_seconds BIGINT                         NOT NULL,
    retry_multiplier      DECIMAL(38, 18)                NOT NULL,
    retry_maximum_seconds BIGINT                         NOT NULL,
    retry_max_attempts    INT                            NOT NULL,
    retry_window_seconds  BIGINT                         NOT NULL,
    occurred_at           TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    expires_at            TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    next_attempt_at       TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    attempt_count         INT                            NOT NULL DEFAULT 0,
    state                 VARCHAR(24) COLLATE "C"        NOT NULL,
    route_fingerprint     CHAR(64) COLLATE "C"           NULL,
    acknowledged_at       TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    row_version           BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (source_version > 0 AND row_version >= 0),
    CHECK (attempt_count >= 0 AND attempt_count <= retry_max_attempts AND retry_max_attempts > 0),
    CHECK (timeout_millis > 0 AND retry_initial_seconds > 0 AND retry_multiplier >= 1
        AND retry_maximum_seconds >= retry_initial_seconds AND retry_window_seconds > 0),
    CHECK (expires_at > occurred_at),
    CHECK (state IN ('INIT', 'PENDING', 'ACKED', 'EXHAUSTED')),
    CHECK ((state = 'ACKED' AND acknowledged_at IS NOT NULL) OR (state <> 'ACKED' AND acknowledged_at IS NULL))
);

CREATE UNIQUE INDEX uk_notification_event_target ON merchant_notification_delivery (tenant_id, event_id, target_ref);

CREATE INDEX ix_notification_order ON merchant_notification_delivery (tenant_id, order_id, occurred_at, id);

CREATE INDEX ix_notification_pending ON merchant_notification_delivery (tenant_id, state, next_attempt_at, id);

CREATE INDEX ix_notification_merchant ON merchant_notification_delivery (tenant_id, payee_merchant_id, occurred_at, id);

CREATE TABLE refund_notification_receipt
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    delivery_id       VARCHAR(64) COLLATE "C"        NOT NULL,
    business_event_id VARCHAR(64) COLLATE "C"        NOT NULL,
    aggregate_id      VARCHAR(64) COLLATE "C"        NOT NULL,
    source_version    BIGINT                         NOT NULL,
    order_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id VARCHAR(64) COLLATE "C"        NOT NULL,
    request_digest    CHAR(64) COLLATE "C"           NOT NULL,
    caller_service_id VARCHAR(64) COLLATE "C"        NOT NULL,
    merchant_event_id CHAR(64) COLLATE "C"           NOT NULL,
    disposition       VARCHAR(24) COLLATE "C"        NOT NULL,
    accepted_at       TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CHECK (source_version > 0),
    CHECK (disposition IN ('ENQUEUED', 'DISABLED'))
);

CREATE UNIQUE INDEX uk_refund_notification_delivery ON refund_notification_receipt (tenant_id, delivery_id);

CREATE INDEX ix_refund_notification_order ON refund_notification_receipt (tenant_id, order_id, accepted_at, id);

CREATE TABLE merchant_notification_wake_receipt
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    delivery_id       VARCHAR(64) COLLATE "C"        NOT NULL,
    event_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    request_key       VARCHAR(128) COLLATE "C"       NOT NULL,
    caller_scope_hash CHAR(64) COLLATE "C"           NOT NULL,
    principal_type    VARCHAR(24) COLLATE "C"        NOT NULL,
    principal_id      VARCHAR(128) COLLATE "C"       NOT NULL,
    reason            VARCHAR(500) COLLATE "C"       NOT NULL,
    request_digest    CHAR(64) COLLATE "C"           NOT NULL,
    result            VARCHAR(24) COLLATE "C"        NOT NULL,
    attempt_count     INT                            NOT NULL,
    delivery_version  BIGINT                         NOT NULL,
    native_task_id    VARCHAR(64) COLLATE "C"        NULL,
    accepted_at       TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CHECK (principal_type IN ('OPERATOR', 'SERVICE')),
    CHECK (attempt_count >= 0 AND delivery_version >= 0),
    CHECK (result IN
           ('REGISTERED', 'RETRIED', 'RESCHEDULED', 'PENDING', 'RUNNING', 'WAITING', 'ACKED', 'EXHAUSTED', 'EXPIRED'))
);

CREATE UNIQUE INDEX uk_notification_wake_request ON merchant_notification_wake_receipt (tenant_id, caller_scope_hash, request_key);

CREATE INDEX ix_notification_wake_delivery ON merchant_notification_wake_receipt (tenant_id, delivery_id, accepted_at, id);

CREATE TABLE checkout_session_revocation_receipt
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    session_id        VARCHAR(64) COLLATE "C"        NOT NULL,
    order_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    request_key       VARCHAR(128) COLLATE "C"       NOT NULL,
    caller_scope_hash CHAR(64) COLLATE "C"           NOT NULL,
    request_digest    CHAR(64) COLLATE "C"           NOT NULL,
    reason            VARCHAR(500) COLLATE "C"       NOT NULL,
    newly_revoked     BOOLEAN                        NOT NULL,
    before_version    BIGINT                         NOT NULL,
    session_version   BIGINT                         NOT NULL,
    revoked_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    accepted_at       TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CHECK (before_version >= 0 AND session_version > 0),
    CHECK ((newly_revoked = TRUE AND session_version - before_version = 1)
        OR (newly_revoked = FALSE AND session_version = before_version))
);

CREATE UNIQUE INDEX uk_session_revocation_request ON checkout_session_revocation_receipt (tenant_id, caller_scope_hash, request_key);

CREATE INDEX ix_session_revocation_history ON checkout_session_revocation_receipt (tenant_id, session_id, accepted_at, id);

CREATE TABLE allocation_recovery_cursor
(
    id         VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    tenant_id  VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted    BOOLEAN                        NOT NULL DEFAULT FALSE,
    order_id   VARCHAR(64) COLLATE "C"        NOT NULL,
    generation BIGINT                         NOT NULL,
    due_at     TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    completed  BOOLEAN                        NOT NULL DEFAULT FALSE,
    CHECK (deleted = FALSE AND generation >= 0)
);

CREATE INDEX ix_allocation_recovery_order ON allocation_recovery_cursor (tenant_id, order_id);

CREATE TABLE acquire_recovery_retry_receipt
(
    id                       VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    tenant_id                VARCHAR(64) COLLATE "C"        NOT NULL,
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
    CHECK (deleted = FALSE AND generation >= 0 AND expected_attempts > 0),
    CHECK (kind IN ('PAYMENT_ALLOCATION', 'ORDER_DEADLINE', 'ORDER_CLOSURE')),
    CHECK (kind <> 'ORDER_DEADLINE' OR generation > 0),
    CHECK (result IN ('RETRIED', 'NO_LONGER_NEEDED'))
);

CREATE UNIQUE INDEX uk_acquire_recovery_retry ON acquire_recovery_retry_receipt (tenant_id, caller_scope_hash, request_key);

CREATE INDEX ix_acquire_recovery_retry_order ON acquire_recovery_retry_receipt (tenant_id, order_id, accepted_at);
