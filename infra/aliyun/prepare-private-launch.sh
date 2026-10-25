#!/bin/sh
# Private preparation only. Does not start Caddy, publish ports or install timers.
set -eu
umask 077
cd /opt/moonletter
test -f .env
test -f compose.private.yaml
test -f compose.https.yaml
test -f Caddyfile.https
domain=${MOON_LETTER_DOMAIN:-$(sed -n 's/^MOON_LETTER_DOMAIN=//p' .env | head -n 1)}
test -n "$domain" || { echo 'Set MOON_LETTER_DOMAIN in the environment or private .env.' >&2; exit 2; }
MOON_LETTER_DOMAIN="$domain" docker compose --env-file .env -f compose.private.yaml -f compose.https.yaml config --quiet
docker run --rm --pull never --network none -e "MOON_LETTER_DOMAIN=$domain" -v /opt/moonletter/Caddyfile.https:/etc/caddy/Caddyfile:ro caddy:2-alpine caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
curl --connect-timeout 3 --max-time 10 -fsS http://127.0.0.1:8080/actuator/health
printf '\nPrivate configuration validated. No public service started.\n'
MOONLETTER_VERIFY_BACKUP=yes sh /opt/moonletter/backup-private.sh
if [ "${MOONLETTER_ENABLE_BACKUP_TIMER:-no}" = yes ]; then
    sh /opt/moonletter/install-private-backup-timer.sh
fi
printf 'Private preparation and isolated restore completed. A local disk backup is not an off-site backup.\n'
