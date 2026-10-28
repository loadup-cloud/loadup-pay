-- Local owner lookups for reconciliation consults; no financial mutation or channel access in SQL.
CREATE INDEX ix_payment_attempt_recon_lookup ON payment_attempt (tenant_id, channel, account_scope_hash, channel_request_no);
CREATE INDEX ix_refund_order_recon_lookup ON refund_order (tenant_id, channel, account_scope_hash,
                                                           original_channel_request_no, channel_refund_no);
