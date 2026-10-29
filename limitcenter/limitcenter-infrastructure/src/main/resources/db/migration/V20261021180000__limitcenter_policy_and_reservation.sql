-- PostgreSQL initial schema; all historical changes are folded into the table definitions.

CREATE TABLE limit_policy_head
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    policy_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id VARCHAR(64) COLLATE "C"        NOT NULL,
    latest_version    INT                            NOT NULL DEFAULT 0,
    row_version       BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (latest_version >= 0 AND row_version >= 0)
);

CREATE UNIQUE INDEX uk_limit_policy_head ON limit_policy_head (tenant_id, policy_id);

CREATE TABLE limit_policy_revision
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    policy_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    policy_version    INT                            NOT NULL,
    policy_hash       CHAR(64) COLLATE "C"           NOT NULL,
    payee_merchant_id VARCHAR(64) COLLATE "C"        NOT NULL,
    compiled_rules    TEXT                           NOT NULL,
    activated_at      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_limit_policy_version ON limit_policy_revision (tenant_id, policy_id, policy_version);

CREATE TABLE limit_rule_runtime
(
    id                  VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id           VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted             BOOLEAN                        NOT NULL DEFAULT FALSE,
    owner_policy_id     VARCHAR(64) COLLATE "C"        NOT NULL,
    policy_version      INT                            NOT NULL,
    rule_scope_id       VARCHAR(64) COLLATE "C"        NOT NULL,
    merchant_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    metric              VARCHAR(24) COLLATE "C"        NOT NULL,
    currency            CHAR(3) COLLATE "C"            NOT NULL,
    window_type         VARCHAR(16) COLLATE "C"        NOT NULL,
    week_start          SMALLINT                       NOT NULL DEFAULT 1,
    timezone            VARCHAR(64) COLLATE "C"        NOT NULL,
    current_limit       BIGINT                         NOT NULL,
    enforcement_enabled BOOLEAN                        NOT NULL DEFAULT TRUE,
    metering_enabled    BOOLEAN                        NOT NULL DEFAULT TRUE,
    row_version         BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (current_limit >= 0 AND week_start BETWEEN 1 AND 7 AND metering_enabled AND policy_version > 0),
    CHECK (currency = 'CNY' AND CHAR_LENGTH(timezone) > 0)
);

CREATE UNIQUE INDEX uk_limit_runtime_scope ON limit_rule_runtime (tenant_id, rule_scope_id, merchant_id, metric, currency, window_type);

CREATE TABLE limit_bucket
(
    id            VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id     VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at    TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted       BOOLEAN                        NOT NULL DEFAULT FALSE,
    rule_scope_id VARCHAR(64) COLLATE "C"        NOT NULL,
    merchant_id   VARCHAR(64) COLLATE "C"        NOT NULL,
    metric        VARCHAR(24) COLLATE "C"        NOT NULL,
    currency      CHAR(3) COLLATE "C"            NOT NULL,
    window_type   VARCHAR(16) COLLATE "C"        NOT NULL,
    window_start  TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    window_end    TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    used_value    BIGINT                         NOT NULL DEFAULT 0,
    held_value    BIGINT                         NOT NULL DEFAULT 0,
    row_version   BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (used_value >= 0 AND held_value >= 0),
    CHECK (window_start < window_end)
);

CREATE UNIQUE INDEX uk_limit_bucket ON limit_bucket (tenant_id, rule_scope_id, merchant_id, metric, currency,
                                                     window_type, window_start,
                                                     window_end);

CREATE INDEX ix_bucket_merchant ON limit_bucket (tenant_id, merchant_id, window_type, window_start);

CREATE TABLE limit_operation_gate
(
    id                        VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id                 VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at                TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at                TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                   BOOLEAN                        NOT NULL DEFAULT FALSE,
    operation_id              VARCHAR(64) COLLATE "C"        NOT NULL,
    payment_id                VARCHAR(64) COLLATE "C"        NOT NULL,
    state                     VARCHAR(24) COLLATE "C"        NOT NULL,
    payment_source_version    BIGINT                         NOT NULL DEFAULT 0,
    no_collection_evidence_id VARCHAR(64) COLLATE "C"        NULL,
    row_version               BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_limit_gate_operation ON limit_operation_gate (tenant_id, operation_id);

CREATE UNIQUE INDEX uk_limit_gate_payment ON limit_operation_gate (tenant_id, payment_id);

CREATE TABLE limit_reservation
(
    id                     VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id              VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at             TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                BOOLEAN                        NOT NULL DEFAULT FALSE,
    operation_id           VARCHAR(64) COLLATE "C"        NOT NULL,
    payment_id             VARCHAR(64) COLLATE "C"        NOT NULL,
    order_id               VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id      VARCHAR(64) COLLATE "C"        NOT NULL,
    amount_minor           BIGINT                         NOT NULL,
    currency               CHAR(3) COLLATE "C"            NOT NULL,
    policy_id              VARCHAR(64) COLLATE "C"        NOT NULL,
    policy_version         INT                            NOT NULL,
    policy_hash            CHAR(64) COLLATE "C"           NOT NULL,
    request_digest         CHAR(64) COLLATE "C"           NOT NULL,
    caller_service_id      VARCHAR(64) COLLATE "C"        NOT NULL,
    initiated_at           TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    state                  VARCHAR(24) COLLATE "C"        NOT NULL,
    reserve_expires_at     TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    execution_epoch        BIGINT                         NOT NULL DEFAULT 0,
    active_attempt_id      VARCHAR(64) COLLATE "C"        NULL,
    payment_source_version BIGINT                         NOT NULL DEFAULT 0,
    row_version            BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (amount_minor > 0 AND currency = 'CNY' AND policy_version > 0 AND reserve_expires_at > initiated_at),
    CHECK (execution_epoch >= 0 AND payment_source_version >= 0 AND row_version >= 0)
);

CREATE UNIQUE INDEX uk_limit_operation ON limit_reservation (tenant_id, operation_id);

CREATE UNIQUE INDEX uk_limit_payment ON limit_reservation (tenant_id, payment_id);

CREATE INDEX ix_reservation_expire ON limit_reservation (tenant_id, state, reserve_expires_at, id);

CREATE TABLE limit_reservation_item
(
    id             VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id      VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at     TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at     TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted        BOOLEAN                        NOT NULL DEFAULT FALSE,
    reservation_id VARCHAR(64) COLLATE "C"        NOT NULL,
    bucket_id      VARCHAR(64) COLLATE "C"        NOT NULL,
    reserved_value BIGINT                         NOT NULL,
    consumed_value BIGINT                         NOT NULL DEFAULT 0,
    restored_value BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (reserved_value > 0 AND consumed_value >= 0 AND restored_value >= 0),
    CHECK (consumed_value <= reserved_value AND restored_value <= consumed_value)
);

CREATE UNIQUE INDEX uk_limit_item_bucket ON limit_reservation_item (tenant_id, reservation_id, bucket_id);

CREATE TABLE limit_execution_grant
(
    id              VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id       VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted         BOOLEAN                        NOT NULL DEFAULT FALSE,
    operation_id    VARCHAR(64) COLLATE "C"        NOT NULL,
    payment_id      VARCHAR(64) COLLATE "C"        NOT NULL,
    attempt_id      VARCHAR(64) COLLATE "C"        NOT NULL,
    execution_epoch BIGINT                         NOT NULL,
    request_key     VARCHAR(128) COLLATE "C"       NOT NULL,
    permit_key_id   VARCHAR(64) COLLATE "C"        NOT NULL,
    permit_hash     CHAR(64) COLLATE "C"           NOT NULL,
    PRIMARY KEY (id),
    CHECK (execution_epoch > 0)
);

CREATE UNIQUE INDEX uk_limit_grant_epoch ON limit_execution_grant (tenant_id, operation_id, execution_epoch);

CREATE UNIQUE INDEX uk_limit_grant_request ON limit_execution_grant (tenant_id, operation_id, request_key);

CREATE TABLE limit_settlement_receipt
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    operation_id      VARCHAR(64) COLLATE "C"        NOT NULL,
    payment_id        VARCHAR(64) COLLATE "C"        NOT NULL,
    kind              VARCHAR(24) COLLATE "C"        NOT NULL,
    business_event_id VARCHAR(64) COLLATE "C"        NOT NULL,
    request_digest    CHAR(64) COLLATE "C"           NOT NULL,
    payment_version   BIGINT                         NOT NULL,
    evidence_id       VARCHAR(64) COLLATE "C"        NOT NULL,
    PRIMARY KEY (id),
    CHECK (kind IN ('CONFIRM', 'RELEASE') AND payment_version > 0)
);

CREATE UNIQUE INDEX uk_limit_settlement_operation ON limit_settlement_receipt (tenant_id, operation_id);

CREATE UNIQUE INDEX uk_limit_settlement_event ON limit_settlement_receipt (tenant_id, business_event_id);

CREATE TABLE limit_refund_receipt
(
    id                                VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    tenant_id                         VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at                        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at                        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted                           BOOLEAN                        NOT NULL DEFAULT FALSE,
    caller_service_id                 VARCHAR(64) COLLATE "C"        NOT NULL,
    operation_id                      VARCHAR(64) COLLATE "C"        NOT NULL,
    payment_id                        VARCHAR(64) COLLATE "C"        NOT NULL,
    refund_id                         VARCHAR(64) COLLATE "C"        NOT NULL,
    refund_version                    BIGINT                         NOT NULL,
    business_event_id                 VARCHAR(64) COLLATE "C"        NOT NULL,
    refund_evidence_id                VARCHAR(64) COLLATE "C"        NOT NULL,
    request_digest                    CHAR(64) COLLATE "C"           NOT NULL,
    cumulative_payment_refunded_minor BIGINT                         NOT NULL,
    verified_payment_refunded_minor   BIGINT                         NOT NULL,
    verified_payment_refund_version   BIGINT                         NOT NULL,
    result                            VARCHAR(24) COLLATE "C"        NOT NULL,
    restored_amount_minor             BIGINT                         NOT NULL,
    restored_payment_count            BOOLEAN                        NOT NULL,
    accepted_at                       TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (
        NOT deleted
            AND refund_version > 0
            AND cumulative_payment_refunded_minor > 0
            AND verified_payment_refunded_minor >= cumulative_payment_refunded_minor
            AND verified_payment_refund_version > 0
            AND restored_amount_minor >= 0
            AND restored_amount_minor <= verified_payment_refunded_minor
            AND (
            (restored_amount_minor > 0 OR restored_payment_count) AND result = 'RESTORED'
                OR restored_amount_minor = 0 AND NOT restored_payment_count AND result = 'NO_ACTION'
            )
        )
);

CREATE UNIQUE INDEX uk_limit_refund_receipt_refund ON limit_refund_receipt (tenant_id, refund_id);

CREATE INDEX ix_limit_refund_receipt_operation ON limit_refund_receipt (tenant_id, operation_id);
