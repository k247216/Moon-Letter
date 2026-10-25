#!/bin/sh
# Preserves database, OSS, identity, environment and HTTPS proxy.
set -eu
umask 077
cd /opt/moonletter
new_jar=moon-letter-private-keepsakes-v32-20261010.jar
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
case "$schema" in 13|14|15) ;; *) echo 'REFUSED: expected database schema 13, 14 or 15.' >&2; exit 2 ;; esac
# Verify recovery before changing the live JAR; no production restore is performed.
MOONLETTER_VERIFY_BACKUP=yes sh /opt/moonletter/backup-private.sh
backup="moon-letter-server-before-v32-$(date -u +%Y%m%dT%H%M%SZ)-$$.jar"
cp -p moon-letter-server.jar "$backup"
chmod 600 "$backup"
on_failure() {
    result=$?
    if [ "$result" -ne 0 ]; then
        docker compose --env-file .env -f compose.private.yaml stop app || true
        current=$(schema_version 2>/dev/null || printf unknown)
        if [ "$schema" = 13 ] && [ "$current" = 13 ]; then
            cp -p "$backup" moon-letter-server.jar
            docker compose --env-file .env -f compose.private.yaml up -d --no-deps --force-recreate app || true
            echo 'Previous JAR restored; verify internal health.' >&2
        else
            echo 'Application stopped safely. Schema 14+ contains private data; old servers must NOT run against it.' >&2
            echo 'Keep database and backup intact. Diagnose or restore a verified full snapshot deliberately; do not run older update scripts.' >&2
        fi
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
test "$(schema_version)" = 15
trap - EXIT
printf '\nV32 backend healthy, schema 15. Database, OSS originals, .env and HTTPS preserved.\n'
printf 'Install V32 over the existing APK on BOTH devices. Do not uninstall or re-pair.\n'
printf 'Do not roll back to a pre-privacy server on schema 14+. Health UP is not two-device acceptance.\n'
