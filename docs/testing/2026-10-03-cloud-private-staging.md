<!-- Deployment/person/device identifiers in this public document are anonymized. -->
# Guangzhou ECS private staging

Confirmed by user: Ubuntu 24.04 amd64, 2 vCPU / 2 GiB, ECS 192.0.2.10; private OSS bucket example-bucket, oss-cn-guangzhou. No domain or filing yet. Base official images imported on ECS after matching archive checksum; SSH transfer path works. Server jar upload verification remains unconfirmed.

Use compose.private.yaml before domain/HTTPS readiness. PostgreSQL has no host port. API host binding is 127.0.0.1:8080 only. No Caddy startup and no public mobile endpoint in this stage. Images use pull_policy never because direct Docker Hub access failed. Containers have memory/PID/log limits; limits are a starting budget, not a proven ECS load test.

Run init-private-env.sh only on ECS to generate local secrets without printing them. It refuses to overwrite an existing .env. Fill only dedicated RAM credentials locally, never paste .env into chat. Validate using `docker compose --env-file .env -f compose.private.yaml config --quiet` (without --quiet, resolved credentials can appear in output). Do not start with missing credentials.

This creates a new staging database, not a migration of existing LAN records/sessions/media. Do not initialize founding members or switch mobile clients yet. Migration, genuine OSS upload/read validation, backup/restore rehearsal and HTTPS remain separate gates. UP alone does not prove OSS access, as the client can be constructed without a network request.

## S3 startup correction

User ECS logs confirmed seven app restarts, OOMKilled=false, PostgreSQL migrated to v11. Root cause: S3ObjectStorage has two constructors and its runtime constructor lacked @Autowired; Spring attempted a nonexistent default constructor. Existing tests manually instantiated the component and missed Spring construction.

Added S3ObjectStorageContextTest: real context plus configured upload URL generation, no OSS network call. It failed with the same No default constructor found before the fix and passed after marking the runtime constructor. Full server suite first run had 15 Windows Tomcat NIO loopback errors across BootstrapAuthenticationTest, SessionRecoveryTest, DeviceReplacementPairingTest, PairingFlowTest, SelfUseRecordingLoopE2ETest, MediaChainTest, SharedPerspectiveSyncTest and TypedSyncOperationTest. With the existing Windows NIO2 opt-in enabled, all 82 tests passed, no failures/errors/skips. Tests used dedicated databases, not the existing LAN data.

Old local running JAR was locked during Maven repackage; added overridable moon-letter.jar-name build property and produced an independent artifact without stopping LAN service. Fresh Linux amd64 Java container with S3 mode and synthetic credentials started the packaged JAR and returned UP at localhost:18081. This proves startup, not actual OSS authorization.

Replacement artifact: server/target/moon-letter-server-s3fix-20261003.jar; SHA256 AB2D9995F28CE66139AA57AF2F33F9DCD10EA25E34804519DB5696EA1E882515. Keep ECS .env and database volume. Stop only app, preserve old JAR, replace JAR and force-recreate app to refresh bind mount. Never use compose down -v. Remote replacement and genuine OSS round-trip remain awaiting user execution.

User subsequently provided successful ECS startup log: startup 22.02s (process 24.689s), restartCount=0, OOMKilled=false; user confirmed subsequent health check success. No remote OSS round-trip result yet.

## Standalone OSS probe

OssDeploymentProbe runs only via explicit CLI launcher, not an HTTP endpoint, and does not create users or access the database. As of V27, it uses the same S3ObjectStorage verified streaming writer for a tiny PNG, verifies metadata SHA-256 and length, then compares downloaded bytes; the older signed-PUT probe is superseded. The random object key is under couples/_deployment_probe/assets/. Retains the tiny object because the app RAM policy intentionally excludes deletion. Main prints only a safe result code and object key, no credentials or signed URLs. This local code change does not mean the V27 probe has been run against production OSS.

Three new tests exercise success, upload failure stopping the read, and changed download content; observed red before implementation, then green. Full suite 85/85; independent probe JAR packaged. Linux launcher with missing configuration returns the expected sanitized failure. Windows PowerShell requires quoting the dotted -Dloader.main argument; first unquoted launcher check failed due to argument parsing, then rerun correctly quoted.

Probe artifact: server/target/moon-letter-oss-probe-20261003.jar; SHA256 E9CFAB6E1DCD0E09CD138D1F8D0B426C90D2671CDE9FCA010CA51D0F9B0D0E83. Run separately using read-only JAR mount and existing ECS .env, no published ports, no replacement of running backend. Local tests do not establish OSS compatibility/authorization; user must execute the real round-trip on ECS.

User confirmed the real ECS OSS probe succeeded. This is user-reported evidence, not independently collected remote output. User additionally confirmed existing backend content is test-only, so historical test-data migration is not required. No database/OSS reset executed.

HTTPS continuation templates compose.https.yaml and Caddyfile.https validated locally: composed project remains moonletter, API localhost bind/database isolation preserved, domain explicitly available to Caddy. Offline Linux Caddy validation passed (formatting warning only); no live certificate/domain test performed. App-level deletion boundaries captured in docs/superpowers/specs/2026-10-03-cloud-deletion-boundaries.md, not implemented yet.
