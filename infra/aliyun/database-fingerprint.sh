#!/bin/sh
# Counts and order-independent content hashes; never prints source row contents.
set -eu
container=${MOONLETTER_POSTGRES_CONTAINER:-moonletter-postgres-1}
database=${1:?database required}
snapshot=${2:-}
case "$container" in ''|*[!a-zA-Z0-9_.-]*) exit 2;; esac
case "$database" in ''|*[!a-zA-Z0-9_]*) exit 2;; esac
case "$snapshot" in *[!a-zA-Z0-9-]*) exit 2;; esac
begin="BEGIN ISOLATION LEVEL REPEATABLE READ; SET LOCAL TIME ZONE 'UTC';"
if [ -n "$snapshot" ]; then begin="BEGIN ISOLATION LEVEL REPEATABLE READ; SET TRANSACTION SNAPSHOT '$snapshot'; SET LOCAL TIME ZONE 'UTC';"; fi
queries=$(docker exec "$container" psql -U moon_letter -d "$database" -qAt -v ON_ERROR_STOP=1 -c "$begin SELECT format('SELECT %L, count(*), md5(coalesce(string_agg(md5(to_jsonb(t)::text), %L ORDER BY md5(to_jsonb(t)::text)), %L)) FROM %I.%I t;',table_schema||'.'||table_name,'','',table_schema,table_name) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name; COMMIT;")
test -n "$queries"
printf '%s\n%s\nCOMMIT;\n' "$begin" "$queries" | docker exec -i "$container" psql -U moon_letter -d "$database" -qAt -F '|' -v ON_ERROR_STOP=1
