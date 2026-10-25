#!/bin/sh
# Read-only owner check. Never sources .env or prints request bodies, tokens, media URLs or raw logs.
set -eu
cd /opt/moonletter
warnings=0
warn() { printf 'WARN: %s\n' "$1"; warnings=$((warnings+1)); }
printf 'UTC time: '; date -u
printf '\nInternal API:\n'
curl --connect-timeout 3 --max-time 10 -fsS http://127.0.0.1:8080/actuator/health || warn 'Internal health unavailable'
printf '\nApplication state:\n'
state=$(docker inspect moonletter-app-1 --format '{{.State.Status}} restarts={{.RestartCount}} oom={{.State.OOMKilled}}')
printf '%s\n' "$state"
case "$state" in running*oom=false) ;; *) warn 'Application stopped or OOM detected';; esac
printf '\nContainer resource usage:\n'
docker stats --no-stream --format '{{.Name}} memory={{.MemUsage}} memoryPercent={{.MemPerc}} cpu={{.CPUPerc}}' moonletter-app-1 moonletter-postgres-1
printf '\nDisk:\n'
df -Pk /opt/moonletter
used=$(df -Pk /opt/moonletter | awk 'END {gsub(/%/, "", $5); print $5}')
if [ "$used" -ge 80 ]; then warn 'Disk at or above 80%; archive verified backups off-site'; fi
printf '\nPrivate port guard:\n'
if ss -lnt | awk 'NR>1 {address=$4; if(address ~ /:(8080|5432)$/ && address !~ /^(127[.]0[.]0[.]1|\[::1\]|::1):/) found=1} END {exit !found}'; then
    warn '8080 or 5432 is listening beyond loopback; inspect Docker/security-group configuration'
else printf 'No public 8080/5432 listener detected on host.\n'; fi
printf '\nBackup scheduler:\n'
if systemctl is-active --quiet moonletter-backup.timer; then
    systemctl show moonletter-backup.timer -p LastTriggerUSec -p NextElapseUSecRealtime
    result=$(systemctl show moonletter-backup.service -p Result --value)
    started=$(systemctl show moonletter-backup.service -p ExecMainStartTimestamp --value)
    if [ -z "$started" ]; then warn 'No actual backup service start recorded'; fi
    printf 'Last service result: %s\n' "$result"
    if [ "$result" != success ]; then warn 'Last backup service failed; inspect its journal privately'; fi
else warn 'Daily backup timer is not active'; fi
latest=$(find /opt/moonletter/backups -maxdepth 1 -type f -name 'moonletter-*.dump' -printf '%T@ %p\n' 2>/dev/null | sort -nr | head -n 1 | cut -d ' ' -f 2-)
if [ -z "$latest" ]; then warn 'No completed database dump found'
else
    printf 'Latest dump: %s\n' "$latest"
    if [ $(( $(date +%s) - $(stat -c %Y "$latest") )) -gt 172800 ]; then warn 'Latest database backup is older than 48 hours'; fi
    if [ ! -s "$latest.fingerprints" ] || [ ! -s "$latest.restore-report.txt" ] || ! cmp -s "$latest.fingerprints" "$latest.restore-report.txt"; then
        warn 'Latest dump has no matching isolated restore report'
    else printf 'Latest isolated restore report matches snapshot fingerprints.\n'; fi
fi
printf '\nServer media queue (not device outbox):\n'
docker exec moonletter-postgres-1 psql -U moon_letter -d moon_letter -v ON_ERROR_STOP=1 -c "SELECT kind,status,count(*) FROM media_asset GROUP BY kind,status; SELECT count(*) AS stale_unready_24h FROM media_asset WHERE status <> 'READY' AND created_at < now()-interval '24 hours';"
printf '\nError lines in last 1000 log lines (count only):\n'
docker logs --tail 1000 moonletter-app-1 2>&1 | awk '/ ERROR |OutOfMemoryError/ {n++} END {print n+0}'
printf '\nWarnings=%s. This is not OSS original protection, off-site recovery, or cross-province acceptance.\n' "$warnings"
if [ "$warnings" -gt 0 ]; then exit 1; fi
