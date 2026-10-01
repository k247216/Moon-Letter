-- V10: a pairing token may name the member slot it reopens.
--
-- A phone that was uninstalled or lost belongs to the space already, so it has
-- to rejoin as the same member: the records written under its id, and the name
-- the other phone already shows, both depend on that. Tokens without a target
-- keep the original meaning — invite somebody who is not in the space yet.

ALTER TABLE space_pairing_code
    ADD COLUMN rejoin_user_id uuid REFERENCES app_user (id);

-- One outstanding token per space already holds via ux_space_pairing_code_outstanding;
-- the new column is only read while consuming a token.
CREATE INDEX space_pairing_code_rejoin_idx
    ON space_pairing_code (rejoin_user_id)
    WHERE rejoin_user_id IS NOT NULL;
