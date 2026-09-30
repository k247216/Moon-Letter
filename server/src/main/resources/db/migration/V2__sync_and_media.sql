CREATE TABLE idempotency_record (
    operation_id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user(id),
    payload_hash char(64) NOT NULL CHECK (payload_hash ~ '^[0-9a-fA-F]{64}$'),
    response_status integer NOT NULL,
    response_body jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE sync_change (
    change_seq bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    couple_id uuid NOT NULL REFERENCES couple_space(id),
    entity_type varchar(40) NOT NULL,
    entity_id uuid NOT NULL,
    operation varchar(20) NOT NULL,
    payload jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX sync_change_space_cursor_idx ON sync_change(couple_id, change_seq);
