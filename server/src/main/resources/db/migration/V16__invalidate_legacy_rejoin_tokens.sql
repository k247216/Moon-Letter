-- Pending REJOIN credentials issued before self-recovery could target the other
-- member. Revoke those credentials when upgrading; keep invitations and history.
UPDATE space_pairing_code
SET consumed_at = now()
WHERE rejoin_user_id IS NOT NULL
  AND consumed_at IS NULL;
