#!/bin/sh
# Preserves real records, pairing, OSS originals, environment and HTTPS.
set -eu
umask 077
cd /opt/moonletter
new_jar=moon-letter-memory-book-integrity-v33-20261010.jar
test -f "$new_jar"
sha256sum -c "$new_jar.sha256"
test -f .env
test -f compose.private.yaml
test -f moon-letter-server.jar
docker compose --env-file .env -f compose.private.yaml config --quiet
schema_version() {
    docker exec moonletter-postgres-1 psql -U moon_letter -d moon_letter -At -v ON_ERROR_STOP=1 -c 'SELECT max(version::integer) FROM flyway_schema_history WHERE success AND version IS NOT NULL;'
}
schema=$(schema_version)
case "$schema" in 13|14|15|16) ;; *) echo 'REFUSED: expected database schema 13 through 16.' >&2; exit 2 ;; esac
# Restore validation is isolated; this never resets the live database.
MOONLETTER_VERIFY_BACKUP=yes sh /opt/moonletter/backup-private.sh
backup="moon-letter-server-before-v33-$(date -u +%Y%m%dT%H%M%SZ)-$$.jar"
cp -p moon-letter-server.jar "$backup"
chmod 600 "$backup"
on_failure() {
    result=$?
    if [ "$result" -ne 0 ]; then
        docker compose --env-file .env -f compose.private.yaml stop app || true
        echo 'Application stopped safely. Database, OSS and prior JAR remain intact.' >&2
        echo 'Do not automatically run an old server: schema 14+ protects private content; schema 16 revokes unsafe recovery codes.' >&2
        echo 'Diagnose or deliberately restore a verified complete snapshot; never run old reset/update scripts.' >&2
        printf 'Previous JAR retained: %s\n' "$backup" >&2
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
printf '\nV33 backend healthy, schema 16. Records, identities, OSS originals, .env and HTTPS preserved.\n'
printf 'Legacy unconsumed recovery codes revoked; existing device sessions remain valid. New recovery codes restore only their issuer.\n'
printf 'Install V33 over the existing APK on BOTH devices. Do not uninstall or re-pair.\n'
printf 'Health UP is not two-device or OSS security acceptance. Do not downgrade the backend.\n'
