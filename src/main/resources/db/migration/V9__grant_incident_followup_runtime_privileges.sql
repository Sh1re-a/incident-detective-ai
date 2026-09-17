DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_catalog.pg_roles
        WHERE rolname = 'incident_detective_runtime'
    ) THEN
        EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE incident_detective.incident_followup_runs, incident_detective.incident_followup_turns FROM incident_detective_runtime';
        EXECUTE 'GRANT SELECT ON TABLE incident_detective.incident_followup_runs, incident_detective.incident_followup_turns TO incident_detective_runtime';
        EXECUTE 'GRANT INSERT (run_reference, mode, scenario_id, answer_state, snapshot_sha256, snapshot_json, expires_at) ON incident_detective.incident_followup_runs TO incident_detective_runtime';
        EXECUTE 'GRANT INSERT (run_reference, client_turn_id, request_sha256, status) ON incident_detective.incident_followup_turns TO incident_detective_runtime';
        EXECUTE 'GRANT UPDATE (status, response_json, completed_at) ON incident_detective.incident_followup_turns TO incident_detective_runtime';
    END IF;
END
$migration$;
