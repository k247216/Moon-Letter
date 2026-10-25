#!/bin/sh
set -eu
cd /opt/moonletter
new_jar=/opt/moonletter/moon-letter-cloud-album-v11-20261003.jar
expected=66cefaf3d581022054be80d20586d96b1b192f113fa2af5804f11aabcc4e8c83
printf '%s  %s\n' "$expected" "$new_jar" | sha256sum -c -
test -f .env
test -f compose.private.yaml
test -f moon-letter-server.jar
docker compose --env-file .env -f compose.private.yaml config --quiet
backup="moon-letter-server-before-v11-$(date +%Y%m%d-%H%M%S).jar"
cp -p moon-letter-server.jar "$backup"
docker compose --env-file .env -f compose.private.yaml stop app
cp "$new_jar" moon-letter-server.jar
chmod 600 moon-letter-server.jar
docker compose --env-file .env -f compose.private.yaml up -d --no-deps --force-recreate app
attempt=0
until curl --connect-timeout 2 --max-time 5 -fsS http://127.0.0.1:8080/actuator/health; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 30 ]; then
        printf '\nHealth check failed. Previous JAR retained: %s\n' "$backup"
        exit 1
    fi
    sleep 2
done
printf '\nV11 backend healthy. Existing .env and PostgreSQL volume preserved; no public ports added.\n'
