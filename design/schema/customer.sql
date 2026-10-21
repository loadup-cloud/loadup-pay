-- DESIGN DRAFT ONLY. Not a Flyway migration. Do not apply to a running database.
-- New pay tables only; framework-owned migrations are intentionally excluded.
-- Session time zone: Asia/Shanghai (+08:00). No database selection or destructive statements.

CREATE TABLE merchant_relationship (
  id VARCHAR(64) NOT NULL,
  tenant_id VARCHAR(64) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  parent_merchant_id VARCHAR(64) NOT NULL,
  child_merchant_id VARCHAR(64) NOT NULL,
  relation_type VARCHAR(24) NOT NULL,
  state VARCHAR(24) NOT NULL,
  valid_from DATETIME(6) NOT NULL,
  valid_to DATETIME(6) NULL,
  row_version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_merchant_relationship (tenant_id,parent_merchant_id,child_merchant_id,relation_type),
  KEY ix_relationship_child (tenant_id,child_merchant_id,state),
  CHECK (parent_merchant_id <> child_merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE merchant_action_grant (
  id VARCHAR(64) NOT NULL,
  tenant_id VARCHAR(64) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  principal_type VARCHAR(24) NOT NULL,
  principal_id VARCHAR(128) NOT NULL,
  payee_merchant_id VARCHAR(64) NOT NULL,
  relationship_id VARCHAR(64) NULL,
  action VARCHAR(32) NOT NULL,
  state VARCHAR(24) NOT NULL,
  valid_from DATETIME(6) NOT NULL,
  valid_to DATETIME(6) NULL,
  row_version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_merchant_action (tenant_id,principal_type,principal_id,payee_merchant_id,action),
  KEY ix_grant_payee (tenant_id,payee_merchant_id,state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
