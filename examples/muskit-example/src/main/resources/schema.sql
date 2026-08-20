CREATE TABLE IF NOT EXISTS muskit_audit (
    event_id VARCHAR(128) PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL,
    action_name VARCHAR(128) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    actor_id VARCHAR(256),
    subject_type VARCHAR(128),
    subject_id VARCHAR(256),
    error_code VARCHAR(128),
    attributes_json CLOB NOT NULL
);
