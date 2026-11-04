-- PostgreSQL initial schema.

CREATE TABLE merchant_profile
(
    id              VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,

    merchant_code   VARCHAR(64) COLLATE "C"        NOT NULL,
    name            VARCHAR(200) COLLATE "C"       NOT NULL,
    short_name      VARCHAR(100) COLLATE "C",
    type            VARCHAR(32) COLLATE "C"        NOT NULL,
    industry        VARCHAR(64) COLLATE "C"        NOT NULL,
    country         VARCHAR(2) COLLATE "C"         NOT NULL,
    province        VARCHAR(64) COLLATE "C",
    city            VARCHAR(64) COLLATE "C",
    address         VARCHAR(500) COLLATE "C",
    registration_no VARCHAR(64) COLLATE "C",
    contact_name    VARCHAR(100) COLLATE "C",
    contact_phone   VARCHAR(32) COLLATE "C",
    contact_email   VARCHAR(200) COLLATE "C",
    status          VARCHAR(16) COLLATE "C"        NOT NULL,
    row_version     BIGINT                         NOT NULL,
    created_by      VARCHAR(256) COLLATE "C"       NOT NULL,
    updated_by      VARCHAR(256) COLLATE "C"       NOT NULL,
    created_at      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted         BOOLEAN                        NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_merchant_profile_code ON merchant_profile (merchant_code);

CREATE INDEX idx_merchant_profile_page ON merchant_profile (status, created_at);
