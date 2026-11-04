-- PostgreSQL initial schema.

CREATE TABLE contract_catalog_version
(
    id          VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,

    kind        VARCHAR(24) COLLATE "C"        NOT NULL,
    code        VARCHAR(128) COLLATE "C"       NOT NULL,
    version     INT                            NOT NULL,
    status      VARCHAR(24) COLLATE "C"        NOT NULL,
    row_version BIGINT                         NOT NULL,
    content     TEXT                           NOT NULL,
    updated_by  VARCHAR(256) COLLATE "C"       NOT NULL,
    created_at  TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at  TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted     BOOLEAN                        NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_contract_catalog_version ON contract_catalog_version (kind, code, version);

CREATE INDEX idx_contract_catalog_page ON contract_catalog_version (kind, status, updated_at);

CREATE TABLE merchant_contract
(
    id              VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,

    merchant_id     VARCHAR(64) COLLATE "C"        NOT NULL,
    scope_key       VARCHAR(128) COLLATE "C"       NOT NULL,
    plan_version_id VARCHAR(64) COLLATE "C"        NOT NULL,
    status          VARCHAR(24) COLLATE "C"        NOT NULL,
    generation      BIGINT                         NOT NULL,
    request_key     VARCHAR(128) COLLATE "C"       NOT NULL,
    request_digest  VARCHAR(64) COLLATE "C"        NOT NULL,
    created_by      VARCHAR(256) COLLATE "C"       NOT NULL,
    created_at      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted         BOOLEAN                        NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_merchant_contract_scope ON merchant_contract (merchant_id, scope_key);

CREATE UNIQUE INDEX uk_merchant_contract_request ON merchant_contract (request_key);

CREATE INDEX idx_merchant_contract_page ON merchant_contract (merchant_id, created_at);

CREATE TABLE merchant_contract_revision
(
    id            VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,

    contract_id   VARCHAR(64) COLLATE "C"        NOT NULL,
    revision      INT                            NOT NULL,
    snapshot      TEXT                           NOT NULL,
    snapshot_hash VARCHAR(128) COLLATE "C"       NOT NULL,
    created_at    TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted       BOOLEAN                        NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_merchant_contract_revision ON merchant_contract_revision (contract_id, revision);
