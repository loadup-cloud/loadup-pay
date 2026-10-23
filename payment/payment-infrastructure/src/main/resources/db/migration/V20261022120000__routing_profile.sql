CREATE TABLE routing_profile
(
    id                VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_id         VARCHAR(64)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    profile_ref       VARCHAR(128) NOT NULL,
    payee_merchant_id VARCHAR(64)  NOT NULL,
    spec_payload      LONGTEXT     NOT NULL,
    content_hash      CHAR(64)     NOT NULL,
    caller_service_id VARCHAR(64)  NOT NULL,
    request_key       VARCHAR(128) NOT NULL,
    request_digest    CHAR(64)     NOT NULL,
    published_at      DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_routing_profile_ref (tenant_id, profile_ref),
    UNIQUE KEY uk_routing_profile_request (tenant_id, caller_service_id, request_key),
    KEY ix_routing_profile_payee (tenant_id, payee_merchant_id, published_at, id),
    CHECK (deleted = 0),
    CHECK (JSON_VALID(spec_payload)),
    CHECK (CHAR_LENGTH(spec_payload) <= 65536)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
