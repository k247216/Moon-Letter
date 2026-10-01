#!/usr/bin/env bash
# Restore a moon-letter backup into a FRESH (empty) database and storage dir.
# Usage: restore.sh <db.dump> [storage.tar.gz]
# Verify counts and sampled hashes against backup_health.tsv afterwards.
set -euo pipefail

DB_DUMP="${1:?usage: restore.sh <db.dump> [storage.tar.gz]}"
STORAGE_DUMP="${2:-}"
DB_URL="${MOON_LETTER_RESTORE_DB_URL:?MOON_LETTER_RESTORE_DB_URL is required (target database must exist and be empty)}"
DB_USER="${MOON_LETTER_DB_USER:?MOON_LETTER_DB_USER is required}"

pg_restore --dbname="$DB_URL" --username="$DB_USER" --no-owner "$DB_DUMP"

if [ -n "$STORAGE_DUMP" ]; then
  tar -xzf "$STORAGE_DUMP" -C "$(dirname "${MOON_LETTER_STORAGE:-./storage}")"
fi

echo "restore complete. Verify with:"
echo "  psql $DB_URL -U $DB_USER -c 'SELECT count(*) FROM entry'"
echo "  compare with the backup_health.tsv line for this dump"
