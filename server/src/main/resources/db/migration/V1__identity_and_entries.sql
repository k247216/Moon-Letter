CREATE TYPE user_status AS ENUM ('ACTIVE', 'DISABLED');
CREATE TYPE space_status AS ENUM ('ACTIVE', 'UNPAIRED', 'CLOSED');
CREATE TYPE theme_kind AS ENUM ('WARM_BEIGE', 'PURE_WHITE');
CREATE TYPE media_kind AS ENUM ('IMAGE', 'VIDEO', 'AUDIO');
CREATE TYPE media_status AS ENUM ('LOCAL_PENDING', 'UPLOADING', 'PROCESSING', 'READY', 'FAILED', 'DELETED');
CREATE TYPE entry_mode AS ENUM ('PERSONAL', 'COLLABORATIVE');
CREATE TYPE entry_state AS ENUM ('DRAFT', 'PUBLISHED', 'CAPSULE_LOCKED', 'ARCHIVED');
CREATE TYPE contribution_role AS ENUM ('OWNER', 'CONTRIBUTOR');
CREATE TYPE block_type AS ENUM ('TEXT', 'IMAGE', 'VIDEO', 'AUDIO', 'MUSIC', 'LOCATION');
CREATE TYPE location_source AS ENUM ('SINGLE_GPS_REQUEST', 'MANUAL_CITY');
CREATE TYPE calendar_type AS ENUM ('GREGORIAN', 'LUNAR');
CREATE TYPE capsule_status AS ENUM ('SEALED', 'OPENED');

CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    status user_status NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE couple_space (
    id uuid PRIMARY KEY,
    status space_status NOT NULL,
    cover_asset_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

CREATE TABLE media_asset (
    id uuid PRIMARY KEY,
    couple_id uuid NOT NULL REFERENCES couple_space(id),
    owner_id uuid NOT NULL REFERENCES app_user(id),
    kind media_kind NOT NULL,
    status media_status NOT NULL,
    object_key varchar(512) NOT NULL,
    mime_type varchar(100) NOT NULL,
    byte_size bigint NOT NULL CHECK (byte_size > 0),
    sha256 char(64) NOT NULL CHECK (sha256 ~ '^[0-9a-fA-F]{64}$'),
    width integer CHECK (width IS NULL OR width > 0),
    height integer CHECK (height IS NULL OR height > 0),
    duration_ms bigint CHECK (duration_ms IS NULL OR duration_ms > 0),
    thumbnail_asset_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

CREATE TABLE user_profile (
    user_id uuid PRIMARY KEY REFERENCES app_user(id),
    display_name varchar(24) NOT NULL CHECK (char_length(btrim(display_name)) BETWEEN 1 AND 24),
    avatar_asset_id uuid REFERENCES media_asset(id),
    theme theme_kind NOT NULL DEFAULT 'WARM_BEIGE',
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE couple_member (
    couple_id uuid NOT NULL REFERENCES couple_space(id),
    user_id uuid NOT NULL REFERENCES app_user(id),
    joined_at timestamptz NOT NULL DEFAULT now(),
    left_at timestamptz,
    deleted_at timestamptz,
    PRIMARY KEY (couple_id, user_id)
);

CREATE OR REPLACE FUNCTION enforce_two_active_members()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.left_at IS NULL AND NEW.deleted_at IS NULL
       AND (SELECT count(*) FROM couple_member
            WHERE couple_id = NEW.couple_id
              AND left_at IS NULL
              AND deleted_at IS NULL) > 2 THEN
        RAISE EXCEPTION 'couple space may contain at most two active members';
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER couple_member_limit
AFTER INSERT OR UPDATE OF left_at, deleted_at ON couple_member
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION enforce_two_active_members();

CREATE TABLE location_snapshot (
    id uuid PRIMARY KEY,
    couple_id uuid NOT NULL REFERENCES couple_space(id),
    created_by uuid NOT NULL REFERENCES app_user(id),
    latitude decimal(8,6) CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    longitude decimal(9,6) CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    city_name varchar(80) NOT NULL,
    country_code char(2) NOT NULL,
    source location_source NOT NULL,
    captured_at timestamptz NOT NULL
);

CREATE TABLE entry (
    id uuid PRIMARY KEY,
    couple_id uuid NOT NULL REFERENCES couple_space(id),
    mode entry_mode NOT NULL,
    state entry_state NOT NULL,
    author_id uuid NOT NULL REFERENCES app_user(id),
    title varchar(120),
    occurred_at timestamptz NOT NULL,
    occurred_timezone varchar(64) NOT NULL,
    current_revision_no integer NOT NULL DEFAULT 0 CHECK (current_revision_no >= 0),
    row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
    location_snapshot_id uuid REFERENCES location_snapshot(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

CREATE TABLE entry_contributor (
    entry_id uuid NOT NULL REFERENCES entry(id),
    user_id uuid NOT NULL REFERENCES app_user(id),
    contribution_role contribution_role NOT NULL,
    PRIMARY KEY (entry_id, user_id)
);

CREATE TABLE entry_block (
    id uuid PRIMARY KEY,
    entry_id uuid NOT NULL REFERENCES entry(id),
    type block_type NOT NULL,
    order_key bigint NOT NULL,
    created_by uuid NOT NULL REFERENCES app_user(id),
    updated_by uuid NOT NULL REFERENCES app_user(id),
    block_version bigint NOT NULL DEFAULT 0 CHECK (block_version >= 0),
    payload jsonb NOT NULL,
    asset_id uuid REFERENCES media_asset(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    UNIQUE (entry_id, order_key),
    CHECK (type <> 'TEXT' OR (jsonb_typeof(payload -> 'text') = 'string'
        AND char_length(payload ->> 'text') BETWEEN 1 AND 20000))
);

CREATE TABLE entry_revision (
    id uuid PRIMARY KEY,
    entry_id uuid NOT NULL REFERENCES entry(id),
    revision_no integer NOT NULL CHECK (revision_no >= 0),
    base_revision_no integer NOT NULL CHECK (base_revision_no >= 0),
    edited_by uuid NOT NULL REFERENCES app_user(id),
    snapshot jsonb NOT NULL,
    change_summary varchar(200),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (entry_id, revision_no)
);

CREATE TABLE comment (
    id uuid PRIMARY KEY,
    entry_id uuid NOT NULL REFERENCES entry(id),
    author_id uuid NOT NULL REFERENCES app_user(id),
    body varchar(2000) NOT NULL CHECK (char_length(btrim(body)) BETWEEN 1 AND 2000),
    reply_to_id uuid REFERENCES comment(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

CREATE TABLE album (
    id uuid PRIMARY KEY,
    couple_id uuid NOT NULL REFERENCES couple_space(id),
    title varchar(80) NOT NULL CHECK (char_length(btrim(title)) BETWEEN 1 AND 80),
    cover_asset_id uuid REFERENCES media_asset(id),
    created_by uuid NOT NULL REFERENCES app_user(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

CREATE TABLE album_item (
    album_id uuid NOT NULL REFERENCES album(id),
    media_asset_id uuid NOT NULL REFERENCES media_asset(id),
    order_key bigint NOT NULL,
    added_by uuid NOT NULL REFERENCES app_user(id),
    added_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (album_id, media_asset_id),
    UNIQUE (album_id, order_key)
);

CREATE TABLE anniversary (
    id uuid PRIMARY KEY,
    couple_id uuid NOT NULL REFERENCES couple_space(id),
    title varchar(80) NOT NULL CHECK (char_length(btrim(title)) BETWEEN 1 AND 80),
    calendar_type calendar_type NOT NULL,
    month smallint NOT NULL,
    day smallint NOT NULL,
    lunar_leap_month boolean NOT NULL DEFAULT false,
    start_year smallint,
    reminder_days smallint[] NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((calendar_type = 'LUNAR' AND month BETWEEN 1 AND 12 AND day BETWEEN 1 AND 30)
        OR (calendar_type = 'GREGORIAN' AND month BETWEEN 1 AND 12 AND day BETWEEN 1 AND 31))
);

CREATE TABLE time_capsule (
    id uuid PRIMARY KEY,
    entry_id uuid NOT NULL UNIQUE REFERENCES entry(id),
    locked_by uuid NOT NULL REFERENCES app_user(id),
    unlock_at timestamptz NOT NULL,
    status capsule_status NOT NULL,
    opened_at timestamptz,
    CHECK (opened_at IS NULL OR status = 'OPENED')
);

CREATE INDEX entry_timeline_idx ON entry(couple_id, occurred_at DESC, id DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX entry_block_entry_order_idx ON entry_block(entry_id, order_key)
    WHERE deleted_at IS NULL;
CREATE INDEX media_asset_space_status_idx ON media_asset(couple_id, status)
    WHERE deleted_at IS NULL;
