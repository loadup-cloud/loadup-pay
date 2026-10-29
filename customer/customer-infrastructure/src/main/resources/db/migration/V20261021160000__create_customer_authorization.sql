-- Customer-owned tables. Framework components own merchant_profile, global_unique and sequence allocation.
-- Business sessions use the deployment system default.

CREATE TABLE merchant_relationship
(
    id                 VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted            BOOLEAN                        NOT NULL DEFAULT FALSE,
    parent_merchant_id VARCHAR(64) COLLATE "C"        NOT NULL,
    child_merchant_id  VARCHAR(64) COLLATE "C"        NOT NULL,
    relation_type      VARCHAR(24) COLLATE "C"        NOT NULL,
    state              VARCHAR(24) COLLATE "C"        NOT NULL,
    valid_from         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    valid_to           TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    row_version        BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (parent_merchant_id <> child_merchant_id),
    CHECK (relation_type = 'MANAGEMENT'),
    CHECK (state IN ('INIT', 'ACTIVE', 'REVOKED')),
    CHECK (row_version >= 0),
    CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE TABLE merchant_action_grant
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL,
    tenant_id         VARCHAR(64) COLLATE "C"        NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    principal_type    VARCHAR(24) COLLATE "C"        NOT NULL,
    principal_id      VARCHAR(128) COLLATE "C"       NOT NULL,
    payee_merchant_id VARCHAR(64) COLLATE "C"        NOT NULL,
    relationship_id   VARCHAR(64) COLLATE "C"        NULL,
    action            VARCHAR(32) COLLATE "C"        NOT NULL,
    state             VARCHAR(24) COLLATE "C"        NOT NULL,
    valid_from        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    valid_to          TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    row_version       BIGINT                         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (state IN ('INIT', 'ACTIVE', 'REVOKED')),
    CHECK (principal_type IN ('MERCHANT_APP', 'MERCHANT', 'OPERATOR')),
    CHECK (action IN ('CREATE_ORDER', 'REFUND', 'QUERY', 'MANAGE_BINDING')),
    CHECK (row_version >= 0),
    CHECK (valid_to IS NULL OR valid_to > valid_from)
);

-- INIT and ACTIVE relationships/grants occupy the original generated active slot.
CREATE UNIQUE INDEX uk_merchant_relationship ON merchant_relationship
    (tenant_id, parent_merchant_id, child_merchant_id, relation_type) WHERE state IN ('INIT', 'ACTIVE');
CREATE INDEX ix_relationship_child ON merchant_relationship (tenant_id, child_merchant_id, state);
CREATE UNIQUE INDEX uk_merchant_action ON merchant_action_grant
    (tenant_id, principal_type, principal_id, payee_merchant_id, action) WHERE state IN ('INIT', 'ACTIVE');
CREATE INDEX ix_grant_payee ON merchant_action_grant (tenant_id, payee_merchant_id, state);
