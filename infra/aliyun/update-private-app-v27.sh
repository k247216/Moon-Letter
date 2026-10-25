#!/bin/sh
# Private backend only. No public proxy, APK, signature, configuration or volume change.
set -eu
umask 077
cd /opt/moonletter
new_jar=moon-letter-hardening-v27-20261005.jar
test -f "$new_jar"
sha256sum -c "$new_jar.sha256"
test -f .env
test -f compose.private.yaml
test -f moon-letter-server.jar
docker compose --env-file .env -f compose.private.yaml config --quiet
schema=$(docker exec moonletter-postgres-1 psql -U moon_letter -d moon_letter -At -v ON_ERROR_STOP=1 -c 'SELECT max(version::integer) FROM flyway_schema_history WHERE success AND version IS NOT NULL;')
if [ "$schema" != 13 ]; then echo 'REFUSED: V27 expects the verified V13 database; investigate before updating.' >&2; exit 2; fi
# A backup file alone is insufficient: verify in an isolated database before stopping the app.
MOONLETTER_VERIFY_BACKUP=yes sh /opt/moonletter/backup-private.sh
backup="moon-letter-server-before-v27-$(date -u +%Y%m%dT%H%M%SZ)-$$.jar"
cp -p moon-letter-server.jar "$backup"
rollback() {
    result=$?
    if [ "$result" -ne 0 ]; then
        echo 'Update failed; restoring previous application JAR. Database is not rolled back.' >&2
        docker compose --env-file .env -f compose.private.yaml stop app || true
        cp -p "$backup" moon-letter-server.jar
        docker compose --env-file .env -f compose.private.yaml up -d --no-deps --force-recreate app || true
        printf 'Retained previous JAR: %s. Check internal health; this is not rollback acceptance.\n' "$backup" >&2
    fi
}
trap rollback EXIT
docker compose --env-file .env -f compose.private.yaml stop app
cp "$new_jar" moon-letter-server.jar
chmod 600 moon-letter-server.jar
docker compose --env-file .env -f compose.private.yaml up -d --no-deps --force-recreate app
attempt=0
until curl --connect-timeout 2 --max-time 5 -fsS http://127.0.0.1:8080/actuator/health 2>/dev/null; do
    attempt=$((attempt+1)); if [ "$attempt" -ge 30 ]; then echo 'New application did not become healthy.' >&2; exit 1; fi
    sleep 2
done
trap - EXIT
printf '\nV27 internal health UP; previous JAR retained. No public ports, APK or signatures changed.\n'
printf 'Wait for old signed PUT URLs to expire (15 minutes), then test the authenticated OSS flow.\n'
printf 'Run: sh /opt/moonletter/check-private-health.sh. Local health is not cross-province acceptance.\n'
