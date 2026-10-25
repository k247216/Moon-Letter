#!/bin/sh
# Container-local PostgreSQL authentication; never source/print .env or keys.
set -eu
export MSYS_NO_PATHCONV=1
umask 077
container=${MOONLETTER_POSTGRES_CONTAINER:-moonletter-postgres-1}
database=${MOONLETTER_BACKUP_DATABASE:-moon_letter}
directory=${MOONLETTER_BACKUP_DIR:-/opt/moonletter/backups}
case "$container" in ''|*[!a-zA-Z0-9_.-]*) echo 'Invalid container' >&2; exit 2;; esac
case "$database" in ''|*[!a-zA-Z0-9_]*) echo 'Invalid database' >&2; exit 2;; esac
mkdir -p "$directory"
directory=$(cd "$directory" && pwd -P)
case "$directory" in /|/opt|/root) echo 'Dedicated backup directory required' >&2; exit 2;; esac
chmod 700 "$directory"
limit=${MOONLETTER_BACKUP_MAX_COPIES:-30}
case "$limit" in ''|*[!0-9]*) exit 2;; esac
copies=$(find "$directory" -maxdepth 1 -type f -name 'moonletter-*.dump' | wc -l)
if [ "$copies" -ge "$limit" ]; then echo 'Backup capacity limit reached: archive verified copies off-site before continuing.' >&2; exit 3; fi
available=$(df -Pk "$directory" | awk 'END {print $4}')
required=$(docker exec "$container" psql -U moon_letter -d "$database" -At -c 'SELECT ceil(pg_database_size(current_database()) / 1024.0)::bigint * 2 + 524288;')
if [ "$available" -lt "$required" ]; then echo 'Insufficient safe backup capacity; archive copies off-site.' >&2; exit 3; fi
dump=$(mktemp "$directory/moonletter-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX.dump")
complete=no
snapshot_dir="/tmp/moonletter_snapshot_$(date -u +%Y%m%d%H%M%S)_$$"
exporter=no
cleanup() {
    if [ "$exporter" = yes ]; then docker exec "$container" touch "$snapshot_dir/stop" || true; fi
    if [ "$complete" != yes ]; then rm -f "$dump" "$dump.sha256" "$dump.manifest" "$dump.fingerprints"; fi
}
trap cleanup EXIT HUP INT TERM
# Keep the exporting transaction inside Linux, independent of Windows Docker stdin buffering.
docker exec -d "$container" sh -c '
set -eu
directory=$1; database=$2
umask 077
mkdir "$directory"; mkfifo "$directory/input"
exec 3<> "$directory/input"
psql -U moon_letter -d "$database" -qAt -v ON_ERROR_STOP=1 < "$directory/input" > "$directory/token" &
exporter=$!
cleanup() { printf "ROLLBACK;\n\\\\q\n" >&3 || true; wait "$exporter" || true; exec 3>&-; rm -f "$directory/input" "$directory/token" "$directory/stop"; rmdir "$directory"; }
trap cleanup EXIT HUP INT TERM
printf "BEGIN ISOLATION LEVEL REPEATABLE READ;\nSELECT pg_export_snapshot();\n" >&3
attempt=0
while [ ! -f "$directory/stop" ] && [ "$attempt" -lt 300 ]; do attempt=$((attempt+1)); sleep 1; done
' sh "$snapshot_dir" "$database"
exporter=yes
attempt=0
snapshot=''
while [ -z "$snapshot" ]; do
    attempt=$((attempt + 1)); if [ "$attempt" -gt 15 ]; then echo 'Cannot obtain consistent backup snapshot' >&2; exit 1; fi
    sleep 1
    snapshot=$(docker exec "$container" sh -c 'test -s "$1/token" && head -n 1 "$1/token"' sh "$snapshot_dir" 2>/dev/null || true)
done
case "$snapshot" in ''|*[!a-zA-Z0-9-]*) exit 2;; esac
docker exec "$container" pg_dump -U moon_letter -d "$database" --snapshot="$snapshot" --format=custom > "$dump"
fingerprinter=${MOONLETTER_BACKUP_FINGERPRINTER:-$(dirname "$0")/database-fingerprint.sh}
sh "$fingerprinter" "$database" "$snapshot" > "$dump.fingerprints"
printf 'database=%s\nsnapshot=%s\n' "$database" "$snapshot" > "$dump.manifest"
cat "$dump.fingerprints" >> "$dump.manifest"
test -s "$dump"
docker exec -i "$container" pg_restore --list < "$dump" >/dev/null
(cd "$directory" && sha256sum "$(basename "$dump")" "$(basename "$dump").manifest" "$(basename "$dump").fingerprints" > "$(basename "$dump").sha256")
docker exec "$container" touch "$snapshot_dir/stop"
exporter=no
complete=yes
if [ "${MOONLETTER_VERIFY_BACKUP:-no}" = yes ]; then
    verifier=${MOONLETTER_BACKUP_VERIFIER:-/opt/moonletter/verify-private-backup.sh}
    test -f "$verifier"
    sh "$verifier" "$dump"
fi
printf 'Backup complete: %s\nVerify using: sh /opt/moonletter/verify-private-backup.sh "%s"\n' "$dump" "$dump"
