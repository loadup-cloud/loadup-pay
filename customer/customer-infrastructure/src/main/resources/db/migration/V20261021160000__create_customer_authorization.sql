-- Customer-owned tables. Framework components own merchant_profile, global_unique and sequence allocation.
-- Business sessions use Asia/Shanghai (+08:00). Requires MySQL 8.0.16 or later.

CREATE TABLE merchant_relationship
(
    id                 VARCHAR(64) NOT NULL,
    tenant_id          VARCHAR(64) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    deleted            TINYINT     NOT NULL DEFAULT 0,
    parent_merchant_id VARCHAR(64) NOT NULL,
    child_merchant_id  VARCHAR(64) NOT NULL,
    relation_type      VARCHAR(24) NOT NULL,
    state              VARCHAR(24) NOT NULL,
    valid_from         DATETIME(6) NOT NULL,
    valid_to           DATETIME(6) NULL,
    row_version        BIGINT      NOT NULL DEFAULT 0,
    active_slot        TINYINT GENERATED ALWAYS AS (CASE WHEN state IN ('INIT', 'ACTIVE') THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_relationship (tenant_id, parent_merchant_id, child_merchant_id, relation_type, active_slot),
    KEY ix_relationship_child (tenant_id, child_merchant_id, state),
    CHECK (parent_merchant_id <> child_merchant_id),
    CHECK (relation_type = 'MANAGEMENT'),
    CHECK (state IN ('INIT', 'ACTIVE', 'REVOKED')),
    CHECK (row_version >= 0),
    CHECK (valid_to IS NULL OR valid_to > valid_from)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE merchant_action_grant
(
    id                VARCHAR(64)  NOT NULL,
    tenant_id         VARCHAR(64)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    principal_type    VARCHAR(24)  NOT NULL,
    principal_id      VARCHAR(128) NOT NULL,
    payee_merchant_id VARCHAR(64)  NOT NULL,
    relationship_id   VARCHAR(64)  NULL,
    action            VARCHAR(32)  NOT NULL,
    state             VARCHAR(24)  NOT NULL,
    valid_from        DATETIME(6)  NOT NULL,
    valid_to          DATETIME(6)  NULL,
    row_version       BIGINT       NOT NULL DEFAULT 0,
    active_slot       TINYINT GENERATED ALWAYS AS (CASE WHEN state IN ('INIT', 'ACTIVE') THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_action (tenant_id, principal_type, principal_id, payee_merchant_id, action, active_slot),
    KEY ix_grant_payee (tenant_id, payee_merchant_id, state),
    CHECK (state IN ('INIT', 'ACTIVE', 'REVOKED')),
    CHECK (principal_type IN ('MERCHANT_APP', 'MERCHANT', 'OPERATOR')),
    CHECK (action IN ('CREATE_ORDER', 'REFUND', 'QUERY', 'MANAGE_BINDING')),
    CHECK (row_version >= 0),
    CHECK (valid_to IS NULL OR valid_to > valid_from)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
