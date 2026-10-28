-- PostgreSQL initial schema; all historical changes are folded into the table definitions.

CREATE TABLE order_admission
(
    id                    VARCHAR(64) COLLATE "C"  NOT NULL,
    tenant_id             VARCHAR(64) COLLATE "C"  NOT NULL,
    created_at            TIMESTAMP(6) WITHOUT TIME ZONE  NOT NULL,
    updated_at            TIMESTAMP(6) WITHOUT TIME ZONE  NOT NULL,
    deleted               SMALLINT      NOT NULL DEFAULT 0,
    order_id              VARCHAR(64) COLLATE "C"  NOT NULL,
    payee_merchant_id     VARCHAR(64) COLLATE "C"  NOT NULL,
    product_scope_key     VARCHAR(128) COLLATE "C" NOT NULL,
    request_digest        CHAR(64) COLLATE "C"     NOT NULL,
    amount_minor          BIGINT       NOT NULL,
    currency              VARCHAR(3) COLLATE "C"   NOT NULL,
    requested_due_at      TIMESTAMP(6) WITHOUT TIME ZONE  NULL,
    caller_scope_hash     CHAR(64) COLLATE "C"     NOT NULL,
    contract_id           VARCHAR(64) COLLATE "C"  NULL,
    contract_revision     INT          NULL,
    contract_generation   BIGINT       NULL,
    merchant_fact_version BIGINT       NOT NULL DEFAULT 0,
    authorization_version BIGINT       NOT NULL DEFAULT 0,
    state                 VARCHAR(24) COLLATE "C"  NOT NULL,
    reason_code           VARCHAR(64) COLLATE "C"  NOT NULL,
    admitted_at           TIMESTAMP(6) WITHOUT TIME ZONE  NULL,
    materialize_before    TIMESTAMP(6) WITHOUT TIME ZONE  NULL,
    snapshot_hash         VARCHAR(128) COLLATE "C" NULL,
    compiled_terms_hash   CHAR(64) COLLATE "C"     NULL,
    compiled_terms        TEXT     NULL,
    PRIMARY KEY (id),
    CHECK (amount_minor > 0 AND currency = 'CNY'),
    CHECK (merchant_fact_version >= 0 AND authorization_version >= 0),
    CHECK (state IN ('INIT', 'ADMITTED', 'DENIED')),
    CHECK ((state = 'ADMITTED' AND contract_id IS NOT NULL AND contract_revision IS NOT NULL AND contract_revision > 0
        AND contract_generation IS NOT NULL AND contract_generation > 0 AND snapshot_hash IS NOT NULL AND
            compiled_terms_hash IS NOT NULL
        AND compiled_terms IS NOT NULL AND admitted_at IS NOT NULL AND materialize_before IS NOT NULL
        AND materialize_before > admitted_at)
        OR (state IN ('INIT', 'DENIED') AND contract_id IS NULL AND contract_revision IS NULL
            AND contract_generation IS NULL AND snapshot_hash IS NULL AND compiled_terms_hash IS NULL
            AND compiled_terms IS NULL AND admitted_at IS NULL AND materialize_before IS NULL)),
    CHECK (deleted IN (0, 1))
);

CREATE UNIQUE INDEX uk_admission_order ON order_admission (tenant_id, order_id);

CREATE INDEX ix_admission_contract ON order_admission (tenant_id, contract_id, created_at, id);

CREATE TABLE contract_limit_publication
(
    id                VARCHAR(64) COLLATE "C" NOT NULL,
    tenant_id         VARCHAR(64) COLLATE "C" NOT NULL,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           SMALLINT     NOT NULL DEFAULT 0,
    contract_id       VARCHAR(64) COLLATE "C" NOT NULL,
    contract_revision INT         NOT NULL,
    policy_id         VARCHAR(64) COLLATE "C" NOT NULL,
    policy_version    INT         NOT NULL,
    policy_hash       CHAR(64) COLLATE "C"    NOT NULL,
    state             VARCHAR(24) COLLATE "C" NOT NULL,
    activated_at      TIMESTAMP(6) WITHOUT TIME ZONE NULL,
    row_version       BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (contract_revision > 0 AND policy_version > 0 AND row_version >= 0),
    CHECK (state IN ('INIT', 'PUBLISHING', 'ACTIVE', 'FAILED')),
    CHECK (state <> 'ACTIVE' OR activated_at IS NOT NULL),
    payee_merchant_id          VARCHAR(64) COLLATE "C"  NULL,
    product_scope_key          VARCHAR(128) COLLATE "C" NULL,
    snapshot_hash              VARCHAR(128) COLLATE "C" NULL,
    activation_payload         TEXT     NULL,
    outbox_event_id            VARCHAR(64) COLLATE "C"  NULL,
    requested_at               TIMESTAMP(6) WITHOUT TIME ZONE  NULL,
    publication_schema_version INT          NOT NULL DEFAULT 0,
    CHECK (publication_schema_version IN (0, 1)),
    CHECK (publication_schema_version = 0 OR (
        publication_schema_version = 1 AND payee_merchant_id IS NOT NULL AND product_scope_key IS NOT NULL
            AND snapshot_hash IS NOT NULL AND activation_payload IS NOT NULL AND requested_at IS NOT NULL
            AND contract_revision = 1 AND policy_version = 1
            AND ((state = 'INIT' AND outbox_event_id IS NULL AND activated_at IS NULL)
            OR (state = 'PUBLISHING' AND outbox_event_id IS NOT NULL AND activated_at IS NULL)
            OR (state = 'ACTIVE' AND outbox_event_id IS NOT NULL AND activated_at IS NOT NULL)))),
    CHECK (deleted IN (0, 1))
);

CREATE UNIQUE INDEX uk_contract_policy_publication ON contract_limit_publication (tenant_id, contract_id, contract_revision);

CREATE UNIQUE INDEX uk_policy_publication_version ON contract_limit_publication (tenant_id, policy_id, policy_version);

CREATE UNIQUE INDEX uk_initial_policy_merchant ON contract_limit_publication (tenant_id, payee_merchant_id);

CREATE UNIQUE INDEX uk_publication_outbox ON contract_limit_publication (tenant_id, outbox_event_id);
