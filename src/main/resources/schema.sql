CREATE TABLE IF NOT EXISTS p_shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

ALTER TABLE p_hub_route
    ADD COLUMN IF NOT EXISTS processing_token UUID;

ALTER TABLE p_hub_route
    ADD COLUMN IF NOT EXISTS build_hub_id UUID;

ALTER TABLE p_hub_route
    ADD COLUMN IF NOT EXISTS route_status VARCHAR(20);

ALTER TABLE p_hub_route
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE p_hub_route
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP;

ALTER TABLE p_hub_route
    ADD COLUMN IF NOT EXISTS error_message TEXT;

ALTER TABLE p_hub_route
    ADD COLUMN IF NOT EXISTS next_refresh_at TIMESTAMP;

ALTER TABLE p_hub_route
    ADD COLUMN IF NOT EXISTS refresh_claimed_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS p_hub_route_build_job (
    hub_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_count INTEGER NOT NULL DEFAULT 0,
    remaining_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    processing_token UUID,
    error_message TEXT,
    created_at TIMESTAMP,
    created_by BIGINT,
    modified_at TIMESTAMP,
    modified_by BIGINT,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT pk_hub_route_build_job PRIMARY KEY (hub_id),
    CONSTRAINT fk_hub_route_build_job_hub FOREIGN KEY (hub_id) REFERENCES p_hub(hub_id)
);

CREATE INDEX IF NOT EXISTS idx_hub_route_build_job_claim
    ON p_hub_route_build_job(status, next_retry_at, created_at);

CREATE INDEX IF NOT EXISTS idx_hub_route_build_status_retry
    ON p_hub_route(build_hub_id, route_status, next_retry_at);

CREATE INDEX IF NOT EXISTS idx_hub_route_refresh_due
    ON p_hub_route(route_status, next_refresh_at, refresh_claimed_at);
