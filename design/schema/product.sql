-- DESIGN DRAFT ONLY. Not a Flyway migration. Do not apply to a running database.
-- New pay tables only; framework-owned migrations are intentionally excluded.
-- Session time zone: Asia/Shanghai (+08:00). No database selection or destructive statements.

CREATE TABLE order_admission (
  id VARCHAR(64) NOT NULL,
  tenant_id VARCHAR(64) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  order_id VARCHAR(64) NOT NULL,
  payee_merchant_id VARCHAR(64) NOT NULL,
  request_digest CHAR(64) NOT NULL,
  contract_id VARCHAR(64) NULL,
  contract_revision INT NULL,
  contract_generation BIGINT NULL,
  merchant_fact_version BIGINT NULL,
  state VARCHAR(24) NOT NULL,
  reason_code VARCHAR(64) NOT NULL,
  admitted_at DATETIME(6) NULL,
  materialize_before DATETIME(6) NULL,
  snapshot_hash VARCHAR(128) NULL,
  compiled_terms_hash CHAR(64) NULL,
  compiled_terms JSON NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admission_order (tenant_id,order_id),
  KEY ix_admission_contract (tenant_id,contract_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE contract_limit_publication (
  id VARCHAR(64) NOT NULL,
  tenant_id VARCHAR(64) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  contract_id VARCHAR(64) NOT NULL,
  contract_revision INT NOT NULL,
  policy_id VARCHAR(64) NOT NULL,
  policy_version INT NOT NULL,
  policy_hash CHAR(64) NOT NULL,
  state VARCHAR(24) NOT NULL,
  activated_at DATETIME(6) NULL,
  row_version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_contract_policy_publication (tenant_id,contract_id,contract_revision),
  UNIQUE KEY uk_policy_publication_version (tenant_id,policy_id,policy_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
