-- Original frozen binding and fixed merchant refund number locate a bounded callback candidate.
ALTER TABLE refund_order
    ADD INDEX ix_refund_callback_lookup (tenant_id, binding_revision_id, channel_refund_no);
