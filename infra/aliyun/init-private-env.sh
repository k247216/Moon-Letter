#!/bin/sh
# Generate only on the target ECS. Never print credentials or overwrite .env.
set -eu
cd "$(dirname "$0")"
if [ -e .env ]; then
    printf '%s\n' '.env already exists; left unchanged.' >&2
    exit 1
fi
command -v openssl >/dev/null
: "${MOON_LETTER_DOMAIN:?Set your API domain before initialization}"
: "${S3_BUCKET:?Set your private OSS bucket before initialization}"
task_db_secret=$(openssl rand -hex 32)
task_bootstrap_secret=$(openssl rand -hex 32)
(
    set -C
    umask 077
    printf '%s\n' \
        "POSTGRES_PASSWORD=$task_db_secret" \
        "MOON_LETTER_DOMAIN=$MOON_LETTER_DOMAIN" \
        "BOOTSTRAP_SECRET=$task_bootstrap_secret" \
        'S3_ENDPOINT=https://oss-cn-guangzhou.aliyuncs.com' \
        'S3_REGION=oss-cn-guangzhou' \
        "S3_BUCKET=$S3_BUCKET" \
        'S3_ACCESS_KEY=' \
        'S3_SECRET_KEY=' > .env
)
printf '%s\n' '.env created with private permissions. Dedicated OSS credentials still required; do not start yet.'
