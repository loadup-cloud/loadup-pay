-- Revocation is a permanent security marker, independent of session/order/payment business states.
ALTER TABLE checkout_session
    ADD COLUMN revoked_at DATETIME(6) NULL AFTER expires_at,
    ADD CONSTRAINT ck_checkout_revocation_version CHECK (revoked_at IS NULL OR row_version > 0);

CREATE TABLE checkout_session_revocation_receipt
(
    id                VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted           TINYINT NOT NULL DEFAULT 0,
    session_id        VARCHAR(64) NOT NULL,
    order_id          VARCHAR(64) NOT NULL,
    request_key       VARCHAR(128) NOT NULL,
    caller_scope_hash CHAR(64) NOT NULL,
    request_digest    CHAR(64) NOT NULL,
    reason            VARCHAR(500) NOT NULL,
    newly_revoked     TINYINT NOT NULL,
    before_version    BIGINT NOT NULL,
    session_version   BIGINT NOT NULL,
    revoked_at        DATETIME(6) NOT NULL,
    accepted_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_revocation_request (tenant_id, caller_scope_hash, request_key),
    KEY ix_session_revocation_history (tenant_id, session_id, accepted_at, id),
    CHECK (newly_revoked IN (0, 1)),
    CHECK (before_version >= 0 AND session_version > 0),
    CHECK ((newly_revoked = 1 AND session_version - before_version = 1)
        OR (newly_revoked = 0 AND session_version = before_version))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin;
