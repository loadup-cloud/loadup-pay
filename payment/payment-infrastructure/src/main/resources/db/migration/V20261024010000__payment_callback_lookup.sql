CREATE INDEX ix_attempt_callback_request
    ON payment_attempt (tenant_id, binding_revision_id, channel_request_no);
