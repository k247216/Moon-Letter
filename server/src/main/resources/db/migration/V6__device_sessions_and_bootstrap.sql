-- V6: device sessions and bootstrap identity (self-use M1 spec 2026-10-01 §5).
-- Clients authenticate with opaque bearer tokens; only the SHA-256 token hash
-- is persisted. At most one active session per member in M1.

CREATE TABLE device_session (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user(id),
    couple_id uuid REFERENCES couple_space(id),
    token_hash char(64) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz,
    revoked_at timestamptz
);

CREATE UNIQUE INDEX ux_device_session_active_user
    ON device_session(user_id)
    WHERE revoked_at IS NULL;

-- Spec 2026-10-01 §6.4 widens display_name to 1-40 Unicode characters.
ALTER TABLE user_profile ALTER COLUMN display_name TYPE varchar(40);

DO $$
DECLARE check_name text;
BEGIN
    SELECT conname INTO check_name
    FROM pg_constraint
    WHERE conrelid = 'user_profile'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%1 AND 24%';
    IF check_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE user_profile DROP CONSTRAINT %I', check_name);
    END IF;
END $$;

ALTER TABLE user_profile
    ADD CONSTRAINT user_profile_display_name_range
    CHECK (char_length(btrim(display_name)) BETWEEN 1 AND 40);
