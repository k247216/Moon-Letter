ALTER TABLE entry ADD COLUMN visibility varchar(7) NOT NULL DEFAULT 'SHARED'
    CHECK (visibility IN ('SHARED', 'PRIVATE'));
ALTER TABLE entry ADD CONSTRAINT entry_private_personal
    CHECK (visibility <> 'PRIVATE' OR mode = 'PERSONAL');
ALTER TABLE occasion ADD COLUMN visibility varchar(7) NOT NULL DEFAULT 'SHARED'
    CHECK (visibility IN ('SHARED', 'PRIVATE'));
ALTER TABLE occasion DROP CONSTRAINT occasion_check;
ALTER TABLE occasion ADD CONSTRAINT occasion_recipient_visibility_check CHECK (
    (kind = 'SURPRISE' AND (
        (visibility = 'PRIVATE' AND recipient_id IS NULL)
        OR (recipient_id IS NOT NULL AND recipient_id <> author_id)))
    OR (kind <> 'SURPRISE' AND recipient_id IS NULL)
);
ALTER TABLE sync_change ADD COLUMN audience_user_id uuid REFERENCES app_user(id);
