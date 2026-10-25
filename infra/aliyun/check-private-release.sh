#!/bin/sh
set -eu
cd /opt/moonletter
test -f .env
test -f moon-letter-server.jar
test -f compose.private.yaml
docker compose --env-file .env -f compose.private.yaml config --quiet
grep -q '^S3_ENDPOINT=https://' .env || { echo 'Check that OSS uses an HTTPS endpoint; configuration not printed.' >&2; exit 2; }
printf 'Backend file hash:\n'
sha256sum moon-letter-server.jar
printf 'Backend state/restarts/OOM:\n'
docker inspect moonletter-app-1 --format '{{.State.Status}} restarts={{.RestartCount}} oom={{.State.OOMKilled}}'
curl --connect-timeout 3 --max-time 10 -fsS http://127.0.0.1:8080/actuator/health
printf '\nDatabase schema and aggregate counts (no credentials or media links):\n'
docker exec moonletter-postgres-1 psql -U moon_letter -d moon_letter -v ON_ERROR_STOP=1 -c "SELECT max(version::integer) AS migration FROM flyway_schema_history WHERE success AND version IS NOT NULL; SELECT count(*) AS albums FROM album; SELECT count(*) AS photos FROM album_item; SELECT kind,status,count(*) FROM media_asset GROUP BY kind,status; SELECT count(*) AS important_days FROM occasion;"
printf 'Read-only private release check completed; this is not a public or OSS round-trip acceptance.\n'
