#!/bin/sh
# Optional stationery metadata only; no database migration or user-data reset.
set -eu
umask 077
cd /opt/moonletter
new_jar=moon-letter-note-stationery-v44-20261026.jar
test -f "$new_jar"
sha256sum -c "$new_jar.sha256"
test -f .env
test -f compose.private.yaml
test -f moon-letter-server.jar
docker compose --env-file .env -f compose.private.yaml config --quiet
schema_version() {
    docker exec moonletter-postgres-1 psql -U moon_letter -d moon_letter -At -v ON_ERROR_STOP=1 -c 'SELECT max(version::integer) FROM flyway_schema_history WHERE success AND version IS NOT NULL;'
}
test "$(schema_version)" = 16 || { echo 'REFUSED: expected schema 16; no update applied.' >&2; exit 2; }
MOONLETTER_VERIFY_BACKUP=yes sh /opt/moonletter/backup-private.sh
backup="moon-letter-server-before-v44-$(date -u +%Y%m%dT%H%M%SZ)-$$.jar"
cp -p moon-letter-server.jar "$backup"
chmod 600 "$backup"
on_failure() {
    result=$?
    if [ "$result" -ne 0 ]; then
        docker compose --env-file .env -f compose.private.yaml stop app || true
        echo 'Application stopped safely. Database, OSS, .env, HTTPS and prior JAR are retained.' >&2
        printf 'Previous JAR: %s\n' "$backup" >&2
        echo 'Inspect failure before restoring the prior JAR; do not use an unrelated older backend.' >&2
    fi
}
trap on_failure EXIT
docker compose --env-file .env -f compose.private.yaml stop app
cp "$new_jar" moon-letter-server.jar
chmod 600 moon-letter-server.jar
docker compose --env-file .env -f compose.private.yaml up -d --no-deps --force-recreate app
attempt=0
until curl --connect-timeout 2 --max-time 5 -fsS http://127.0.0.1:8080/actuator/health 2>/dev/null; do
    attempt=$((attempt+1))
    if [ "$attempt" -ge 30 ]; then echo 'New application did not become healthy.' >&2; exit 1; fi
    sleep 2
done
test "$(schema_version)" = 16
trap - EXIT
printf '\nV44 healthy. Schema 16, data, .env, OSS and HTTPS preserved.\n'
printf 'Cover-install V44 on both devices; do not uninstall, clear data or re-pair.\n'
printf 'Verify a new coloured note on the other device. Health UP is not a two-device acceptance.\n'
