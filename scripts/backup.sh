#!/usr/bin/env bash
# Daily backup for a self-hosted moon-letter server (Task 13).
# Backs up the PostgreSQL database and, in local mode, the local object storage
# directory. In S3 mode the database dump is also copied to Alibaba OSS; media
# objects remain in the private OSS bucket and are protected by its retention
# and versioning policy.
# Install as a systemd timer / cron entry so it runs without anyone remembering it.
#
# Required environment:
#   MOON_LETTER_DB_URL    e.g. postgresql://localhost:5432/moon_letter
#   MOON_LETTER_DB_USER   e.g. moon_letter
#   PGPASSWORD            database password
#   MOON_LETTER_STORAGE   path to the server's storage directory (default ./storage)
#   MOON_LETTER_STORAGE_MODE local|s3 (default local)
#   MOON_LETTER_BACKUP_DIR  where backups accumulate (default /var/backups/moon-letter)
#   MOON_LETTER_OSS_URI    optional oss://bucket/prefix destination
#   OSS_ACCESS_KEY_ID      ossutil 2.x credential (prefer an ECS RAM role)
#   OSS_ACCESS_KEY_SECRET  ossutil 2.x credential (prefer an ECS RAM role)
#   OSS_REGION             OSS region id, e.g. oss-cn-shanghai
#   OSS_ENDPOINT           OSS endpoint, e.g. oss-cn-shanghai.aliyuncs.com
set -euo pipefail

STAMP="$(date +%Y%m%d-%H%M%S)"
STORAGE_DIR="${MOON_LETTER_STORAGE:-./storage}"
BACKUP_DIR="${MOON_LETTER_BACKUP_DIR:-/var/backups/moon-letter}"
STORAGE_MODE="${MOON_LETTER_STORAGE_MODE:-local}"
OSS_URI="${MOON_LETTER_OSS_URI:-}"
OSSUTIL_BIN="${OSSUTIL_BIN:-ossutil}"
DB_URL="${MOON_LETTER_DB_URL:?MOON_LETTER_DB_URL is required}"
DB_USER="${MOON_LETTER_DB_USER:?MOON_LETTER_DB_USER is required}"

mkdir -p "$BACKUP_DIR"

DB_DUMP="$BACKUP_DIR/db-$STAMP.dump"
STORAGE_DUMP="$BACKUP_DIR/storage-$STAMP.tar.gz"

pg_dump --format=custom --file="$DB_DUMP" --dbname="$DB_URL" --username="$DB_USER"

STORAGE_STATUS="not-applicable"
if [ "$STORAGE_MODE" = "s3" ]; then
  STORAGE_STATUS="oss-primary"
elif [ -d "$STORAGE_DIR" ]; then
  tar -czf "$STORAGE_DUMP" -C "$(dirname "$STORAGE_DIR")" "$(basename "$STORAGE_DIR")"
  STORAGE_STATUS="local-archive"
else
  echo "warn: storage dir $STORAGE_DIR missing; database-only backup" >&2
  STORAGE_STATUS="missing"
fi

OSS_STATUS="not-configured"
if [ -n "$OSS_URI" ]; then
  command -v "$OSSUTIL_BIN" >/dev/null 2>&1 || {
    echo "oss backup requested but $OSSUTIL_BIN was not found" >&2
    exit 1
  }
  : "${OSS_ACCESS_KEY_ID:?OSS_ACCESS_KEY_ID is required when MOON_LETTER_OSS_URI is set}"
  : "${OSS_ACCESS_KEY_SECRET:?OSS_ACCESS_KEY_SECRET is required when MOON_LETTER_OSS_URI is set}"
  : "${OSS_REGION:?OSS_REGION is required when MOON_LETTER_OSS_URI is set}"
  : "${OSS_ENDPOINT:?OSS_ENDPOINT is required when MOON_LETTER_OSS_URI is set}"

  OSS_DEST="${OSS_URI%/}/postgres/db-$STAMP.dump"
  "$OSSUTIL_BIN" cp "$DB_DUMP" "$OSS_DEST"
  if [ -f "$STORAGE_DUMP" ]; then
    "$OSSUTIL_BIN" cp "$STORAGE_DUMP" "${OSS_URI%/}/storage/"
  fi
  OSS_STATUS="uploaded"
fi

SHA="$(sha256sum "$DB_DUMP" | cut -d' ' -f1)"
BYTES="$(stat -c%s "$DB_DUMP")"

# One queryable health line per successful run (Task 13 H5).
  echo "$STAMP	db=$BYTES	sha256=$SHA	storage=$STORAGE_STATUS	oss=$OSS_STATUS" \
  >> "$BACKUP_DIR/backup_health.tsv"

# Keep the last 30 runs.
ls -1t "$BACKUP_DIR"/db-*.dump | tail -n +31 | xargs -r rm -f
if compgen -G "$BACKUP_DIR/storage-*.tar.gz" > /dev/null; then
  ls -1t "$BACKUP_DIR"/storage-*.tar.gz | tail -n +31 | xargs -r rm -f
fi

echo "backup complete: $DB_DUMP ($BYTES bytes, sha256=$SHA)"
