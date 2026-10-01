-- V9: session recovery audit (spec 2026-10-01 §5.4 lock-out protection).
-- Records local-admin recovery actions without any token material.
CREATE TABLE session_admin_audit (
    id uuid PRIMARY KEY,
    action varchar(32) NOT NULL,
    target_user_id uuid NOT NULL REFERENCES app_user(id),
    note varchar(200),
    performed_at timestamptz NOT NULL DEFAULT now()
);
