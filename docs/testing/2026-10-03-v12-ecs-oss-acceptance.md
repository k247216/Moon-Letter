<!-- Deployment/person/device identifiers in this public document are anonymized. -->
# V12 ECS / private OSS acceptance

## Verified 2026-10-03

- Physical Android device `182e3fcb` is now bound to the ECS service through the SSH / USB tunnel at `http://127.0.0.1:18088`, not the LAN service.
- Cloud session selects an isolated database. Legacy LAN database and its original photos remain untouched.
- Explicitly opted-in `EcsAlbumAcceptanceTest` passed on the physical phone (1 test, 41.806 seconds).
- Created a test album named `云端链路验收` with one copied original photo. Album ID: `3a37d37d-6920-4a07-b4e7-99f081e71569`.
- The test requires an HTTPS upload ticket targeting exactly `bucket.example.invalid`; it cannot pass against local filesystem media storage.
- Upload completed, ECS acknowledged the photo, and an authenticated download matched the original SHA-256. The shared album API returned the acknowledged photo.
- `CloudConnectionActivityTest` passed on emulator `emulator-5556` (1 test). No cloud partner account was created on the emulator.

## Pending / boundaries

- Actual partner-device pairing, album reception, and download still require acceptance on the second phone. Preserve its member slot; do not consume it with an automated emulator pairing.
- SSH tunnel proves the remote backend and private OSS chain, not independent mobile-network accessibility. Unplugging USB or closing SSH makes this loopback address unavailable.
- Public HTTPS / filing remains pending. Do not expose plaintext HTTP or describe this test as completed cross-province mobile acceptance.
- Legacy LAN content is not automatically uploaded into the cloud space.

## Artifact

`android/app/build/outputs/apk/debug/MoonLetter-cloud-connect-v12-20261003.apk`

SHA-256: `AFC8C6BD83CACF501E957ED6CB45C15B1043236E97868198E300EE0FD5B806CF`

No passwords, bootstrap secrets, bearer tokens, pairing codes, or signed OSS URLs are recorded here.
