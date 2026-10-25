#!/bin/sh
set -eu
cd /opt/moonletter
test -f backup-private.sh
test -f verify-private-backup.sh
test -f moonletter-backup.service
test -f moonletter-backup.timer
test "$(id -u)" -eq 0
install -m 600 moonletter-backup.service /etc/systemd/system/moonletter-backup.service
install -m 600 moonletter-backup.timer /etc/systemd/system/moonletter-backup.timer
systemctl daemon-reload
systemctl enable --now moonletter-backup.timer
systemctl list-timers moonletter-backup.timer --no-pager
printf 'Daily local backup timer enabled. Verify one restore and copy backups off this ECS; no off-site protection is claimed.\n'
