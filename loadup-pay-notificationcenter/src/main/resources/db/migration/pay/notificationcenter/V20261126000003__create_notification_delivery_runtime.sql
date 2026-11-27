-- NotificationCenter owns the Task queue and ID allocation tables in its deployment schema.

CREATE TABLE IF NOT EXISTS loadup_sequence_allocation
(
    id            VARCHAR(64) COLLATE "C"  NOT NULL PRIMARY KEY,

    created_at    TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    deleted       BOOLEAN                  NOT NULL DEFAULT FALSE,
    scope_key     VARCHAR(160) COLLATE "C" NOT NULL,
    max_allocated BIGINT                   NOT NULL,
    max_value     BIGINT                   NOT NULL,
    CONSTRAINT chk_loadup_sequence_bounds CHECK (max_allocated >= 0 AND max_allocated <= max_value AND max_value > 0),
    CONSTRAINT chk_loadup_sequence_deleted CHECK (NOT deleted)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_loadup_sequence_scope ON loadup_sequence_allocation (scope_key);

CREATE TABLE IF NOT EXISTS loadup_task
(
    id              VARCHAR(64) COLLATE "C" PRIMARY KEY,
    created_at      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    task_type       VARCHAR(128) COLLATE "C" NOT NULL,
    biz_id          VARCHAR(128) COLLATE "C" NOT NULL,
    payload         TEXT,
    trace_id        VARCHAR(64),
    next_execute_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    status          VARCHAR(16)              NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'FAILED', 'CANCELLED')),
    attempts        INT                      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts    INT                      NOT NULL CHECK (max_attempts > 0),
    lease_until     TIMESTAMP(6) WITHOUT TIME ZONE,
    claim_token     VARCHAR(64),
    last_error      VARCHAR(256)
);
CREATE INDEX IF NOT EXISTS loadup_task_ready ON loadup_task(status,next_execute_at,id);
CREATE INDEX IF NOT EXISTS loadup_task_lease ON loadup_task(status,lease_until,id);
CREATE UNIQUE INDEX IF NOT EXISTS loadup_task_business ON loadup_task(task_type,biz_id);
CREATE TABLE IF NOT EXISTS loadup_task_operation
(
    id          VARCHAR(64) PRIMARY KEY,
    created_at  TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at  TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    task_id     VARCHAR(64)  NOT NULL,
    operator_id VARCHAR(64)  NOT NULL,
    reason      VARCHAR(512) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_loadup_task_operation_task_id ON loadup_task_operation(task_id);
