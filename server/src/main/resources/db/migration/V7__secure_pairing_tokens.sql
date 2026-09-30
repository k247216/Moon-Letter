-- Task 3: replace the enumerable 6-digit pairing code with a
-- cryptographically random token (>= 128 bits of SecureRandom entropy,
-- stored only as a SHA-256 hash, 15-minute single-use lifetime).
-- The old scheme also allowed only one row per couple; replacement requires
-- keeping revoked history, so uniqueness moves to "one outstanding token".

ALTER TABLE space_pairing_code RENAME COLUMN code_hash TO token_hash;

ALTER TABLE space_pairing_code DROP CONSTRAINT IF EXISTS space_pairing_code_couple_id_key;
ALTER TABLE space_pairing_code DROP CONSTRAINT IF EXISTS space_pairing_code_code_hash_key;

DROP INDEX IF EXISTS space_pairing_code_active_idx;

CREATE UNIQUE INDEX ux_space_pairing_code_outstanding
    ON space_pairing_code(couple_id)
    WHERE consumed_at IS NULL;
