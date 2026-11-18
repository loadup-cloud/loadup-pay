-- Retire queue history, framework envelope versions and the unused task consumer inbox.
DELETE FROM loadup_task WHERE status = 'SUCCEEDED' OR deleted = TRUE;

ALTER TABLE loadup_task
    DROP CONSTRAINT loadup_task_status_check,
    DROP COLUMN deleted,
    DROP COLUMN payload_version,
    DROP COLUMN dedup_key,
    DROP COLUMN execute_at,
    DROP COLUMN retry_delay_ms,
    DROP COLUMN lease_ms,
    DROP COLUMN exponential,
    ADD CONSTRAINT loadup_task_status_check CHECK (status IN ('PENDING', 'RUNNING', 'FAILED', 'CANCELLED'));

DROP INDEX idx_task_business;
CREATE UNIQUE INDEX loadup_task_business ON loadup_task(task_type, biz_id);

ALTER TABLE loadup_task_operation DROP COLUMN deleted;
DROP TABLE loadup_task_inbox;
