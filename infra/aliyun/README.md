# Alibaba Cloud student ECS deployment

This is the first production shape for the mainland-China, price-sensitive deployment:

Current preparation uses `compose.private.yaml` and the V27 private-hardening guide. The older public deployment examples below are reference only: do not activate them before the project's filing/HTTPS gate has been confirmed. Public activation must use the explicit gated script.

- one Alibaba Cloud student ECS instance runs Caddy, Spring Boot and PostgreSQL;
- Alibaba Cloud OSS stores private media originals. Current verified database backups remain on ECS disk; independent/off-site copies and OSS original protection still require owner verification;
- V27 clients upload through the authenticated API; the backend verifies and streams to private OSS. No overwrite-capable OSS PUT URL or OSS credential is handed to the app;
- a later migration to Alibaba Cloud RDS only changes `JDBC_DATABASE_URL`, username and password.

## One-time Alibaba Cloud setup

1. Create the student ECS instance and restrict its security group to SSH from your own IP, plus TCP 80 and 443. Do not expose PostgreSQL or the Spring Boot port.
2. Create a private OSS bucket in the same region as ECS. Keep public read disabled and enable versioning/lifecycle according to the retention you want.
3. Create a dedicated RAM identity with only the bucket permissions needed for this app: object read/write/head and backup write/list. Do not use the Alibaba account's primary AccessKey.
4. Point a domain's DNS record at the ECS public IP. Caddy obtains HTTPS automatically once DNS and ports 80/443 are ready.

## Deploy

Build the server jar locally, copy it to this directory as `moon-letter-server.jar`, then copy `.env.example` to `.env` on ECS and fill every value:

```bash
cp .env.example .env
chmod 600 .env
docker compose --env-file .env -f compose.yaml config --quiet
docker compose --env-file .env -f compose.yaml up -d
docker compose --env-file .env -f compose.yaml ps
curl --fail https://$MOON_LETTER_DOMAIN/actuator/health
```

The `config` command is an intentional preflight: it catches missing secrets before starting containers. Do not put `.env` in the repository or APK.

## Backup contract

On the ECS host install Alibaba's current `ossutil` and configure it through its environment/profile mechanism. Set `MOON_LETTER_OSS_URI`, `OSS_ENDPOINT` and `OSS_REGION` in the shell that invokes `scripts/backup.sh`. The script retains a local copy for recovery and uploads the PostgreSQL dump to the private OSS backup prefix. In S3 mode, media bytes already live in OSS; bucket versioning and lifecycle/retention are therefore part of the backup policy.

Run a restore rehearsal against a fresh database before treating the deployment as recoverable:

```bash
MOON_LETTER_DB_URL='postgresql://postgres-host/moon_letter' \
MOON_LETTER_DB_USER=moon_letter \
PGPASSWORD='...' \
MOON_LETTER_STORAGE_MODE=s3 \
MOON_LETTER_OSS_URI='oss://bucket/backups/moon-letter' \
  bash ../../scripts/backup.sh
```

Never paste AccessKey values into command arguments or commit them. Prefer a RAM role attached to ECS if the account setup supports it; otherwise use a dedicated minimum-permission RAM credential stored only in the host environment.
