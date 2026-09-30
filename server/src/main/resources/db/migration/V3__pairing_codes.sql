CREATE TABLE space_pairing_code (
    id uuid PRIMARY KEY,
    couple_id uuid NOT NULL UNIQUE REFERENCES couple_space(id),
    code_hash char(64) NOT NULL UNIQUE CHECK (code_hash ~ '^[0-9a-fA-F]{64}$'),
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX space_pairing_code_active_idx
    ON space_pairing_code(code_hash, expires_at)
    WHERE consumed_at IS NULL;
