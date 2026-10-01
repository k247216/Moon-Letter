#!/usr/bin/env bash
# Daily backup for a self-hosted moon-letter server (Task 13).
# Backs up the PostgreSQL database and the local object storage directory.
# Install as a systemd timer / cron entry so it runs without anyone remembering it.
#
# Required environment:
#   MOON_LETTER_DB_URL    e.g. postgresql://localhost:5432/moon_letter
#   MOON_LETTER_DB_USER   e.g. moon_letter
#   PGPASSWORD            database password
#   MOON_LETTER_STORAGE   path to the server's storage directory (default ./storage)
#   MOON_LETTER_BACKUP_DIR  where backups accumulate (default /var/backups/moon-letter)
set -euo pipefail

STAMP="$(date +%Y%m%d-%H%M%S)"
STORAGE_DIR="${MOON_LETTER_STORAGE:-./storage}"
BACKUP_DIR="${MOON_LETTER_BACKUP_DIR:-/var/backups/moon-letter}"
DB_URL="${MOON_LETTER_DB_URL:?MOON_LETTER_DB_URL is required}"
DB_USER="${MOON_LETTER_DB_USER:?MOON_LETTER_DB_USER is required}"

mkdir -p "$BACKUP_DIR"

DB_DUMP="$BACKUP_DIR/db-$STAMP.dump"
STORAGE_DUMP="$BACKUP_DIR/storage-$STAMP.tar.gz"

pg_dump --format=custom --file="$DB_DUMP" --dbname="$DB_URL" --username="$DB_USER"

if [ -d "$STORAGE_DIR" ]; then
  tar -czf "$STORAGE_DUMP" -C "$(dirname "$STORAGE_DIR")" "$(basename "$STORAGE_DIR")"
else
  echo "warn: storage dir $STORAGE_DIR missing; database-only backup" >&2
fi

SHA="$(sha256sum "$DB_DUMP" | cut -d' ' -f1)"
BYTES="$(stat -c%s "$DB_DUMP")"

# One queryable health line per successful run (Task 13 H5).
echo "$STAMP	db=$BYTES	sha256=$SHA	storage=$([ -f "$STORAGE_DUMP" ] && echo yes || echo no)" \
  >> "$BACKUP_DIR/backup_health.tsv"

# Keep the last 30 runs.
ls -1t "$BACKUP_DIR"/db-*.dump | tail -n +31 | xargs -r rm -f
ls -1t "$BACKUP_DIR"/storage-*.tar.gz 2>/dev/null | tail -n +31 | xargs -r rm -f

echo "backup complete: $DB_DUMP ($BYTES bytes, sha256=$SHA)"
