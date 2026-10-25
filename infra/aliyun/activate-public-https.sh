#!/bin/sh
set -eu
# Explicit human filing confirmation is required. Not invoked during preparation.
if [ "${MOONLETTER_FILING_APPROVED:-}" != yes ]; then
    echo 'REFUSED: confirm domain/APP filing before activating public HTTPS.' >&2
    exit 2
fi
umask 077
cd /opt/moonletter
test -f .env
domain=${MOON_LETTER_DOMAIN:-$(sed -n 's/^MOON_LETTER_DOMAIN=//p' .env | head -n 1)}
test -n "$domain" || { echo 'Set MOON_LETTER_DOMAIN in the environment or private .env.' >&2; exit 2; }
export MOON_LETTER_DOMAIN="$domain"
docker compose --env-file .env -f compose.private.yaml -f compose.https.yaml config --quiet
docker run --rm --pull never --network none -e "MOON_LETTER_DOMAIN=$domain" -v /opt/moonletter/Caddyfile.https:/etc/caddy/Caddyfile:ro caddy:2-alpine caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
curl --connect-timeout 3 --max-time 10 -fsS http://127.0.0.1:8080/actuator/health >/dev/null
# Starts only the proxy; never recreates app/database or requests an image pull.
docker compose --env-file .env -f compose.private.yaml -f compose.https.yaml up -d --no-deps --pull never caddy
printf 'Proxy started. Verify certificate and API from a different network before switching phones. No acceptance claim made.\n'
