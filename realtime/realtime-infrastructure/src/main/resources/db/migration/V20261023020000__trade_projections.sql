-- PostgreSQL initial schema; all historical changes are folded into the table definitions.

CREATE TABLE trade_order_projection
(
    id                  VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted             BOOLEAN                        NOT NULL DEFAULT FALSE,
    order_id            VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id   VARCHAR(64) COLLATE "C"        NOT NULL,
    merchant_order_no   VARCHAR(64) COLLATE "C"        NOT NULL,
    state               VARCHAR(24) COLLATE "C"        NOT NULL,
    business_created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    source_version      BIGINT                         NOT NULL,
    content_hash        CHAR(64) COLLATE "C"           NOT NULL,
    payload             TEXT                           NOT NULL,
    snapshot_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (NOT deleted AND source_version >= 0),
    CHECK (payload::json IS NOT NULL AND OCTET_LENGTH(payload) <= 1048576)
);

CREATE UNIQUE INDEX uk_order ON trade_order_projection (order_id);

CREATE INDEX ix_order_page ON trade_order_projection (payee_merchant_id, business_created_at, order_id);

CREATE UNIQUE INDEX uk_merchant_order ON trade_order_projection (payee_merchant_id, merchant_order_no);

CREATE TABLE trade_payment_projection
(
    id                  VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted             BOOLEAN                        NOT NULL DEFAULT FALSE,
    payment_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    order_id            VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id   VARCHAR(64) COLLATE "C"        NOT NULL,
    state               VARCHAR(24) COLLATE "C"        NOT NULL,
    business_created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    source_version      BIGINT                         NOT NULL,
    content_hash        CHAR(64) COLLATE "C"           NOT NULL,
    payload             TEXT                           NOT NULL,
    snapshot_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (NOT deleted AND source_version >= 0),
    CHECK (payload::json IS NOT NULL AND OCTET_LENGTH(payload) <= 1048576)
);

CREATE UNIQUE INDEX uk_payment ON trade_payment_projection (payment_id);

CREATE INDEX ix_payment_page ON trade_payment_projection (payee_merchant_id, business_created_at, payment_id);

CREATE INDEX ix_payment_order ON trade_payment_projection (payee_merchant_id, order_id, payment_id);

CREATE TABLE trade_refund_projection
(
    id                  VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted             BOOLEAN                        NOT NULL DEFAULT FALSE,
    refund_id           VARCHAR(64) COLLATE "C"        NOT NULL,
    refund_request_id   VARCHAR(64) COLLATE "C"        NOT NULL,
    payment_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    order_id            VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id   VARCHAR(64) COLLATE "C"        NOT NULL,
    state               VARCHAR(24) COLLATE "C"        NOT NULL,
    business_created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    source_version      BIGINT                         NOT NULL,
    content_hash        CHAR(64) COLLATE "C"           NOT NULL,
    payload             TEXT                           NOT NULL,
    snapshot_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (NOT deleted AND source_version >= 0),
    CHECK (payload::json IS NOT NULL AND OCTET_LENGTH(payload) <= 1048576)
);

CREATE UNIQUE INDEX uk_refund ON trade_refund_projection (refund_id);

CREATE INDEX ix_refund_page ON trade_refund_projection (payee_merchant_id, business_created_at, refund_id);

CREATE INDEX ix_refund_order ON trade_refund_projection (payee_merchant_id, order_id, refund_id);

CREATE INDEX ix_refund_request ON trade_refund_projection (payee_merchant_id, refund_request_id, refund_id);

CREATE TABLE trade_refund_summary
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    order_id          VARCHAR(64) COLLATE "C"        NOT NULL,
    payee_merchant_id VARCHAR(64) COLLATE "C"        NOT NULL,
    source_version    BIGINT                         NOT NULL,
    content_hash      CHAR(64) COLLATE "C"           NOT NULL,
    payload           TEXT                           NOT NULL,
    snapshot_at       TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (NOT deleted AND source_version >= 0),
    CHECK (payload::json IS NOT NULL AND OCTET_LENGTH(payload) <= 1048576)
);

CREATE UNIQUE INDEX uk_summary ON trade_refund_summary (order_id);

CREATE TABLE trade_projection_receipt
(
    id                VARCHAR(64) COLLATE "C"        NOT NULL PRIMARY KEY,
    created_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted           BOOLEAN                        NOT NULL DEFAULT FALSE,
    delivery_id       VARCHAR(64) COLLATE "C"        NOT NULL,
    business_event_id VARCHAR(64) COLLATE "C"        NOT NULL,
    producer          VARCHAR(32) COLLATE "C"        NOT NULL,
    aggregate_id      VARCHAR(64) COLLATE "C"        NOT NULL,
    source_version    BIGINT                         NOT NULL,
    projected_version BIGINT                         NOT NULL,
    disposition       VARCHAR(24) COLLATE "C"        NOT NULL,
    request_digest    CHAR(64) COLLATE "C"           NOT NULL,
    caller_service_id VARCHAR(64) COLLATE "C"        NOT NULL,
    accepted_at       TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (NOT deleted AND source_version >= 0 AND projected_version >= source_version),
    CHECK (disposition IN ('APPLIED', 'DUPLICATE', 'STALE'))
);

CREATE UNIQUE INDEX uk_projection_delivery ON trade_projection_receipt (producer, delivery_id);

CREATE INDEX ix_projection_source ON trade_projection_receipt (producer, aggregate_id, source_version);
