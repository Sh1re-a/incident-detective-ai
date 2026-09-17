CREATE TABLE incident_followup_runs (
    run_reference VARCHAR(80) PRIMARY KEY,
    mode VARCHAR(32) NOT NULL,
    scenario_id VARCHAR(160) NOT NULL,
    answer_state VARCHAR(40) NOT NULL,
    snapshot_sha256 CHAR(64) NOT NULL,
    snapshot_json JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX incident_followup_runs_expiry_idx
    ON incident_followup_runs (expires_at);

CREATE TABLE incident_followup_turns (
    run_reference VARCHAR(80) NOT NULL
        REFERENCES incident_followup_runs(run_reference) ON DELETE CASCADE,
    client_turn_id VARCHAR(100) NOT NULL,
    request_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL
        CHECK (status IN ('started', 'completed', 'failed')),
    response_json JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (run_reference, client_turn_id),
    CHECK ((status = 'completed' AND response_json IS NOT NULL)
        OR (status <> 'completed' AND response_json IS NULL))
);
