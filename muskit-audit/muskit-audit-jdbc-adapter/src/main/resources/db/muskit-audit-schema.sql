CREATE TABLE muskit_audit (
    event_id VARCHAR(128) PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL,
    action_name VARCHAR(128) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    actor_id VARCHAR(256),
    subject_type VARCHAR(128),
    subject_id VARCHAR(256),
    error_code VARCHAR(128),
    attributes_json TEXT NOT NULL
);

CREATE INDEX idx_muskit_audit_action_time ON muskit_audit (action_name, occurred_at);
