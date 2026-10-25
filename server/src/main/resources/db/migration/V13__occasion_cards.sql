CREATE TABLE occasion (
    id uuid PRIMARY KEY,
    couple_id uuid NOT NULL REFERENCES couple_space(id),
    author_id uuid NOT NULL REFERENCES app_user(id),
    kind varchar(16) NOT NULL CHECK (kind IN ('BIRTHDAY', 'ANNIVERSARY', 'SURPRISE')),
    title varchar(80) NOT NULL CHECK (char_length(btrim(title)) BETWEEN 1 AND 80),
    body text NOT NULL DEFAULT '' CHECK (char_length(body) <= 4000),
    date date NOT NULL,
    repeats_yearly boolean NOT NULL,
    recipient_id uuid REFERENCES app_user(id),
    photo_asset_id uuid REFERENCES media_asset(id),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CHECK ((kind = 'SURPRISE' AND recipient_id IS NOT NULL AND recipient_id <> author_id)
        OR (kind <> 'SURPRISE' AND recipient_id IS NULL))
);
CREATE INDEX occasion_space_date_idx ON occasion(couple_id, date, id) WHERE deleted_at IS NULL;
CREATE INDEX occasion_photo_idx ON occasion(photo_asset_id) WHERE deleted_at IS NULL;

-- Preserve the legacy table/API. A yearless month/day uses leap year 2000.
-- Bad legacy dates and lunar dates cannot be represented as Gregorian DATE;
-- they remain available through the old API, rather than being silently changed.
INSERT INTO occasion(id, couple_id, author_id, kind, title, body, date, repeats_yearly, updated_at)
SELECT a.id, a.couple_id, member.user_id, 'ANNIVERSARY', a.title, '',
       make_date(COALESCE(a.start_year, 2000), a.month, a.day), a.repeats_yearly, a.updated_at
FROM anniversary a
JOIN LATERAL (
    SELECT m.user_id FROM couple_member m JOIN app_user u ON u.id=m.user_id
    WHERE m.couple_id=a.couple_id AND m.left_at IS NULL AND m.deleted_at IS NULL AND u.status='ACTIVE'
    ORDER BY m.joined_at, m.user_id LIMIT 1
) member ON true
WHERE a.calendar_type='GREGORIAN'
  AND COALESCE(a.start_year, 2000) BETWEEN 1 AND 32767
  AND a.month BETWEEN 1 AND 12
  AND a.day BETWEEN 1 AND CASE
      WHEN a.month=2 THEN CASE
          WHEN COALESCE(a.start_year, 2000) % 400=0
            OR (COALESCE(a.start_year, 2000) % 4=0 AND COALESCE(a.start_year, 2000) % 100<>0) THEN 29 ELSE 28 END
      WHEN a.month IN (4,6,9,11) THEN 30 ELSE 31 END;
