-- Independent text letters. Legacy time_capsule and entry remain untouched.
CREATE TABLE capsule_letter (
    id uuid PRIMARY KEY,
    couple_id uuid NOT NULL REFERENCES couple_space(id),
    author_id uuid NOT NULL REFERENCES app_user(id),
    title varchar(80) NOT NULL CHECK (char_length(btrim(title)) BETWEEN 1 AND 80),
    body text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 20000),
    unlock_date date NOT NULL CHECK (unlock_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    visibility varchar(16) NOT NULL CHECK (visibility IN ('SHARED', 'PRIVATE')),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE INDEX capsule_letter_space_date_idx ON capsule_letter(couple_id, unlock_date, id)
    WHERE deleted_at IS NULL;
