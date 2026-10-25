ALTER TABLE album ADD COLUMN category varchar(40) NOT NULL DEFAULT '生活';
ALTER TABLE album ADD CONSTRAINT album_category_valid CHECK (char_length(btrim(category)) BETWEEN 1 AND 40);
ALTER TABLE album_item ADD COLUMN photo_id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE album_item ADD CONSTRAINT album_photo_id_unique UNIQUE(photo_id);
ALTER TABLE album_item ADD COLUMN taken_at_epoch_millis bigint NOT NULL DEFAULT 0;
ALTER TABLE album_item ADD COLUMN deleted_at timestamptz;
CREATE INDEX album_space_updated ON album(couple_id, updated_at);
