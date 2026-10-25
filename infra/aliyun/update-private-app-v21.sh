#!/bin/sh
set -eu
cd /opt/moonletter
new_jar=moon-letter-album-previews-v21-20261004.jar
test -f "$new_jar"
sha256sum -c "$new_jar.sha256"
test -f .env
test -f compose.private.yaml
test -f moon-letter-server.jar
docker compose --env-file .env -f compose.private.yaml config --quiet
backup="moon-letter-server-before-v21-$(date +%Y%m%d-%H%M%S).jar"
cp -p moon-letter-server.jar "$backup"
rollback() {
    result=$?
    if [ "$result" -ne 0 ]; then
        printf '\nUpdate failed; restoring previous application JAR.\n'
        cp -p "$backup" moon-letter-server.jar
        docker compose --env-file .env -f compose.private.yaml up -d --no-deps --force-recreate app || true
        printf 'Previous JAR retained: %s. Check application health/logs.\n' "$backup"
    fi
}
trap rollback EXIT
docker compose --env-file .env -f compose.private.yaml stop app
cp "$new_jar" moon-letter-server.jar
chmod 600 moon-letter-server.jar
docker compose --env-file .env -f compose.private.yaml up -d --no-deps --force-recreate app
attempt=0
until curl --connect-timeout 2 --max-time 5 -fsS http://127.0.0.1:8080/actuator/health 2>/dev/null; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 30 ]; then
        printf '\nNew application did not become healthy.\n'
        exit 1
    fi
    sleep 2
done
trap - EXIT
printf '\nV21 thumbnail backend healthy. Originals, .env and database preserved. No public ports added.\n'
