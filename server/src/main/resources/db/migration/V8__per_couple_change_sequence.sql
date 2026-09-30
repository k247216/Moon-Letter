-- Task 6: per-couple ordered change feed. The global identity cursor lets a
-- client skip committed changes that were allocated before a concurrently
-- committed higher sequence. A per-couple sequence, allocated under a lock on
-- the couple's sync state row inside the mutation transaction, orders changes
-- by commit order and makes the per-couple cursor safe.

CREATE TABLE couple_sync_state (
    couple_id uuid PRIMARY KEY REFERENCES couple_space(id),
    last_space_sequence bigint NOT NULL DEFAULT 0
);

ALTER TABLE sync_change ADD COLUMN space_sequence bigint;

WITH ordered AS (
    SELECT change_seq,
           ROW_NUMBER() OVER (PARTITION BY couple_id ORDER BY change_seq) AS rn
    FROM sync_change
)
UPDATE sync_change s
SET space_sequence = o.rn
FROM ordered o
WHERE s.change_seq = o.change_seq;

INSERT INTO couple_sync_state (couple_id, last_space_sequence)
SELECT couple_id, COALESCE(MAX(space_sequence), 0)
FROM sync_change
GROUP BY couple_id
ON CONFLICT (couple_id) DO NOTHING;

ALTER TABLE sync_change ALTER COLUMN space_sequence SET NOT NULL;

CREATE UNIQUE INDEX ux_sync_change_couple_sequence
    ON sync_change(couple_id, space_sequence);
