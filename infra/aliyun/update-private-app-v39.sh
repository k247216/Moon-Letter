#!/bin/sh
# Preserve real data, identities, private OSS and existing HTTPS configuration.
set -eu
umask 077
cd /opt/moonletter
new_jar=moon-letter-stable-reading-v39-20261013.jar
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
backup="moon-letter-server-before-v39-$(date -u +%Y%m%dT%H%M%SZ)-$$.jar"
cp -p moon-letter-server.jar "$backup"
chmod 600 "$backup"
on_failure() {
    result=$?
    if [ "$result" -ne 0 ]; then
        docker compose --env-file .env -f compose.private.yaml stop app || true
        echo 'Application stopped safely; database, OSS, configuration and prior JAR remain intact.' >&2
        printf 'Previous JAR retained: %s\n' "$backup" >&2
        echo 'Inspect the failure before restoring this retained JAR. Do not use an older unrelated backend.' >&2
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
printf '\nV39 backend healthy; schema 16 unchanged. Data, .env, OSS and HTTPS preserved.\n'
printf 'Next, cover-install V39 on BOTH devices; do not uninstall, clear data or re-pair.\n'
printf 'Health UP does not replace two-device media and notification acceptance.\n'
