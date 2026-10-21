-- Expand the original ORDER-only intent while preserving all existing rows and payloads.
-- Baseline: V20261021210000's first unnamed CHECK is order_create_intent_chk_1 on MySQL.
-- Apply before enabling the checkout route; all readers must support CHECKOUT first.
ALTER TABLE order_create_intent
    ADD COLUMN initial_session_id VARCHAR(64) NULL AFTER entry_mode,
    ADD UNIQUE KEY uk_intent_initial_session (tenant_id, initial_session_id),
    DROP CHECK order_create_intent_chk_1,
    ADD CONSTRAINT ck_intent_entry_state CHECK (
        entry_mode IN ('ORDER', 'CHECKOUT') AND state IN ('INIT', 'COMPLETED', 'REJECTED', 'EXPIRED')),
    ADD CONSTRAINT ck_intent_initial_session CHECK (
        (entry_mode = 'ORDER' AND initial_session_id IS NULL)
        OR (entry_mode = 'CHECKOUT' AND initial_session_id IS NOT NULL));
