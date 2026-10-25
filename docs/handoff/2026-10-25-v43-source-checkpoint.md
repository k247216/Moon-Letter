# V43 source checkpoint

This checkpoint brings the previously local Android, backend, tests and deployment templates into version control. It is a source backup, not a deployment or a backup of the couple's actual records.

## Current release

- Client: V43, `0.1.0+paper-interactions-v43-20261025`.
- Existing server contracts include independent albums, media storage, privacy, important-day cards and time capsules. Database migrations through V16 are preserved in `server/src/main/resources/db/migration/`.
- Latest pre-publication client/core regression:669 tests, no failure/error/skip; Android14 isolated emulator:3 phone and3 landscape-tablet cases. Production cross-province/OEM delivery must not be inferred from these tests.
- Existing APK verification and release scope: `releases/v43/README.md`. Binary packages remain local, not committed.

## Private configuration

- Copy `android/private.properties.example` to `android/private.properties` and fill deployment origins and optional personal welcome text locally. The filled file is ignored. Gradle properties/environment variables of the same names can override it.
- Without that file, origins use reserved `.invalid` examples; cloning must never silently connect to someone else's private deployment.
- Keep `android/local.properties`, server/infra `.env`, signing keys, real databases, uploaded media, logs and transfer archives out of Git. They are retained locally, not deleted by this checkpoint.
- Deployment templates use variables. `Test-PublicEndpoint.ps1` requires an explicit HTTPS origin. One-off reset commands tied to a past private space are local-only.
- Public deployment evidence has been anonymized. Original operational documents are retained in the ignored `infra/local-runtime/publication-private-backup-20261025/` folder.

## Before a future push

Run `scripts/Test-PublishHygiene.ps1 -Staged`, inspect the staged file list and diff, then commit and push the explicit branch without force. The scanner prints categories/paths, never the matched values. A private supplemental pattern policy may live in ignored `infra/local-runtime/`.

Earlier Git history contains development-only credential defaults. This checkpoint removes current fixed defaults but does not rewrite existing history. Any credential ever reused outside development must be rotated; a later deletion does not revoke it.

Private configuration, media and signing keys need a separate access-controlled, encrypted backup outside the working drive; source control deliberately does not protect those files.
