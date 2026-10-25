#!/bin/sh
# Restores ONLY into a freshly-created, generated probe database, never moon_letter.
set -eu
umask 077
dump=${1:?Provide an existing backup dump}
container=${MOONLETTER_POSTGRES_CONTAINER:-moonletter-postgres-1}
case "$container" in ''|*[!a-zA-Z0-9_.-]*) echo 'Invalid container' >&2; exit 2;; esac
test -f "$dump"
directory=$(cd "$(dirname "$dump")" && pwd -P)
filename=$(basename "$dump")
case "$filename" in moonletter-*.dump) ;; *) echo 'Unexpected backup filename' >&2; exit 2;; esac
test -f "$directory/$filename.sha256"
(cd "$directory" && sha256sum -c "$filename.sha256")
docker exec -i "$container" pg_restore --list < "$directory/$filename" >/dev/null
probe="moonletter_restore_probe_$(date -u +%Y%m%d%H%M%S)_$$"
case "$probe" in moonletter_restore_probe_[0-9_]*) ;; *) exit 2;; esac
created=no
queries=$(mktemp "$directory/.restore-queries-XXXXXX")
report="$directory/$filename.restore-report.txt"
cleanup() {
    result=$?
    if [ "$created" = yes ]; then
        docker exec "$container" dropdb -U moon_letter --if-exists "$probe" || echo 'Probe cleanup failed; remove the reported probe manually.' >&2
    fi
    rm -f "$queries"
    exit "$result"
}
trap cleanup EXIT HUP INT TERM
docker exec "$container" createdb -U moon_letter "$probe"
created=yes
docker exec -i "$container" pg_restore -U moon_letter -d "$probe" --no-owner --exit-on-error < "$directory/$filename"
fingerprinter=${MOONLETTER_BACKUP_FINGERPRINTER:-$(dirname "$0")/database-fingerprint.sh}
test -f "$directory/$filename.manifest"
test -f "$directory/$filename.fingerprints"
expected=${MOONLETTER_BACKUP_DATABASE:-moon_letter}
case "$expected" in ''|*[!a-zA-Z0-9_]*) exit 2;; esac
if ! grep -qx "database=$expected" "$directory/$filename.manifest"; then
    echo 'RESTORE FAILED: backup source database does not match expected deployment.' >&2
    exit 1
fi
sh "$fingerprinter" "$probe" > "$report"
test -s "$report"
if ! cmp -s "$directory/$filename.fingerprints" "$report"; then
    echo 'RESTORE FAILED: source snapshot row counts/content fingerprints differ.' >&2
    exit 1
fi
cat "$report"
printf 'RESTORE VERIFIED: isolated database %s matches source snapshot counts/content; production untouched; report %s\n' "$probe" "$report"
