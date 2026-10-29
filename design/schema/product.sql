-- DESIGN DRAFT ONLY. Not a Flyway migration. Do not apply to a running database.
-- New pay tables only; framework-owned migrations are intentionally excluded.
-- Session time zone: the deployment system default. No database selection or destructive statements.

CREATE TABLE order_admission
(
    id                    VARCHAR(64)  NOT NULL,
    tenant_id             VARCHAR(64)  NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    deleted               TINYINT      NOT NULL DEFAULT 0,
    order_id              VARCHAR(64)  NOT NULL,
    payee_merchant_id     VARCHAR(64)  NOT NULL,
    product_scope_key     VARCHAR(128) NOT NULL,
    request_digest        CHAR(64)     NOT NULL,
    amount_minor          BIGINT       NOT NULL,
    currency              VARCHAR(3)   NOT NULL,
    requested_due_at      DATETIME(6)  NULL,
    caller_scope_hash     CHAR(64)     NOT NULL,
    contract_id           VARCHAR(64)  NULL,
    contract_revision     INT          NULL,
    contract_generation   BIGINT       NULL,
    merchant_fact_version BIGINT       NOT NULL DEFAULT 0,
    authorization_version BIGINT       NOT NULL DEFAULT 0,
    state                 VARCHAR(24)  NOT NULL,
    reason_code           VARCHAR(64)  NOT NULL,
    admitted_at           DATETIME(6)  NULL,
    materialize_before    DATETIME(6)  NULL,
    snapshot_hash         VARCHAR(128) NULL,
    compiled_terms_hash   CHAR(64)     NULL,
    compiled_terms        LONGTEXT     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admission_order (tenant_id, order_id),
    KEY                   ix_admission_contract(tenant_id, contract_id, created_at, id),
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
            AND compiled_terms IS NULL AND admitted_at IS NULL AND materialize_before IS NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

CREATE TABLE contract_limit_publication
(
    id                VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted           TINYINT     NOT NULL DEFAULT 0,
    contract_id       VARCHAR(64) NOT NULL,
    contract_revision INT         NOT NULL,
    policy_id         VARCHAR(64) NOT NULL,
    policy_version    INT         NOT NULL,
    policy_hash       CHAR(64)    NOT NULL,
    state             VARCHAR(24) NOT NULL,
    activated_at      DATETIME(6) NULL,
    row_version       BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_policy_publication (tenant_id, contract_id, contract_revision),
    UNIQUE KEY uk_policy_publication_version (tenant_id, policy_id, policy_version),
    CHECK (contract_revision > 0 AND policy_version > 0 AND row_version >= 0),
    CHECK (state IN ('INIT', 'PUBLISHING', 'ACTIVE', 'FAILED')),
    CHECK (state <> 'ACTIVE' OR activated_at IS NOT NULL)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;

-- Additive workflow fields; legacy receipt rows remain version 0 and are not trusted for new admissions.
-- Additive initial quota publication workflow. Old receipt-only rows remain schema 0 and are not trusted for new admissions.
ALTER TABLE contract_limit_publication
    ADD COLUMN payee_merchant_id          VARCHAR(64)  NULL,
    ADD COLUMN product_scope_key          VARCHAR(128) NULL,
    ADD COLUMN snapshot_hash              VARCHAR(128) NULL,
    ADD COLUMN activation_payload         LONGTEXT     NULL,
    ADD COLUMN outbox_event_id            VARCHAR(64)  NULL,
    ADD COLUMN requested_at               DATETIME(6)  NULL,
    ADD COLUMN publication_schema_version INT          NOT NULL DEFAULT 0,
    ADD UNIQUE KEY uk_initial_policy_merchant (tenant_id, payee_merchant_id),
    ADD UNIQUE KEY uk_publication_outbox (tenant_id, outbox_event_id),
    ADD CHECK (publication_schema_version IN (0, 1)),
    ADD CHECK (publication_schema_version = 0 OR (
    publication_schema_version = 1 AND payee_merchant_id IS NOT NULL AND product_scope_key IS NOT NULL
    AND snapshot_hash IS NOT NULL AND activation_payload IS NOT NULL AND requested_at IS NOT NULL
    AND contract_revision = 1 AND policy_version = 1
    AND ((state = 'INIT' AND outbox_event_id IS NULL AND activated_at IS NULL)
    OR (state = 'PUBLISHING' AND outbox_event_id IS NOT NULL AND activated_at IS NULL)
    OR (state = 'ACTIVE' AND outbox_event_id IS NOT NULL AND activated_at IS NOT NULL))));
