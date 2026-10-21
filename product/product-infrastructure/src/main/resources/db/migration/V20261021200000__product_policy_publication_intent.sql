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
