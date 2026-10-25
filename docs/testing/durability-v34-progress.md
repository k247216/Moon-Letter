# V34 existing-flow durability and performance

Scope approved 2026-10-11: no new product features, no design changes, no pairing/signing changes, no production data resets or automatic server deployment.

## Evidence before changes

- Timeline preparation: `durability-v34-timeline-red.log`, 9 tests / 1 failure; reading the archive during date/UI preparation ran on the UI thread.
- Session transport: `durability-v34-efficiency-red.log`, repeated authenticated profile reads opened 3 TCP connections instead of reusing 1. Tokens were individually correct. Redirect rejection must remain intact.
- Album + preview: `durability-v34-album-photo-red.log`, 10 tests / 6 failures. Five album failures reproduce unnecessary second snapshot reads and database writes; one image failure reproduces oversized local preview decoding.
- Server album snapshot: `server/durability-v34-album-read-red.log`, 4 tests / 1 failure. Eight albums consumed 10 SQL executions including membership; budget is 3 regardless of album count. Complete fields, tombstones and isolation controls passed before the change.
- Auxiliary refresh: source trace shows `triggerSync` awaiting profiles/cards/capsules/anniversary inside the foreground mutex. The foreground loop then waits 3 seconds. The new lifecycle-owned refresh callback test initially could not compile because this separation did not exist (`durability-v34-foreground-red.log`); this is API-absence RED, not a measured frame-rate or network latency result.
- Safety RED: `durability-v34-safety-red-corrected.log` confirms 7/10 profile tests failed (stale, ABA, cancellation); both occasion cancellation tests failed before fixes. `durability-v34-transport-red-safety-green.log` then confirmed profiles 10/10 and occasion cancellation 2/2 GREEN; it reproduced 3/4 anniversary failures and text polling creating 3 connections. These were subsequently repaired.
- Foreground serial-wait mutation: `durability-v34-foreground-serial-mutation-red.log` proves the stalled auxiliary job prevents expected text cycles when awaited; the temporary join was removed. `durability-v34-refresh-thread-red.log` additionally caught inherited UI-thread processing, then the job dispatcher was explicitly changed to IO. This supplements the earlier API-absence RED with behavioral regression evidence.

## Bounded repair design

1. Prepare timeline UI data on Default dispatcher; reuse immutable date formatters, remember date chapters, and observe header visibility through derived state. No record count cap.
2. Decode list previews at <=640 pixels and maintain separate preview/detail cache keys. Detail decoding remains <=1440; stored and exported originals unchanged.
3. Share SessionApi transport resources without sharing bearer credentials or HTTP response caches; reject redirects as before.
4. Album cycles always read incoming metadata first. Skip the second full read only when no album/photo/cover mutation was attempted. Even ambiguous failed mutations still trigger a confirming read. Compare merged rows before writing, preserving local originals, pending cover selections and deletion tombstones.
5. Server reads photos in one same-space batch and albums in one query after membership authorization. API/schema unchanged.
6. Foreground text cycles no longer await auxiliary HTTP requests. Only one auxiliary refresh runs per foreground lifecycle on IO; leaving the lifecycle cancels it. Background/manual compatibility paths remain available. Profile/avatar and anniversary cache publishing rejects cancellation and obsolete binding generations, including A→B→A; slow avatar writes stage unique private files outside the UI session lock before protected atomic publication.

## Verification limits / remaining work

- No Android device was connected at inspection; actual scroll frame timing and cross-province response times are not measured.
- All builds/tests must be serialized; tests use synthetic local databases/HTTP responses only.
- Do not claim production updated, real OSS access validated, or notifications guaranteed.
- Whole-album snapshots still grow with library size; batching and no-op merge reduce work without introducing a new sync protocol.
- Missing-file/permanent-error recovery UX and safe long-term cache/export orphan cleanup remain follow-up work, not silently implemented via deleting originals.

## Final verification

- Backend complete suite/package: `server/durability-v34-server-full.log`, 191 tests, 0 failures/errors/skipped. Schema remains 16; no new migrations.
- Final Android complete suite/package: `durability-v34-android-full-final.log`, BUILD SUCCESSFUL after the avatar IO refactor. All 394 tests passed, 0 failures/errors/skipped; application and instrumentation APKs built. Instrumentation tests were compiled, not executed on a device.
- Updater shell syntax validated in a network-disabled container. APK versionCode 34, min SDK 26, target SDK 37; certificate fingerprint matches existing V33 signature (`74001d60036e79f897f761523c7cdbd87580b0a04256125973ee4bf66fd8a015`). No signing configuration changed.
- Independent review caught auxiliary UI-thread inheritance and avatar file IO under the shared binding lock; both were corrected before final packaging. Final read-only review passed with no Critical/Important findings. A non-blocking server snapshot-consistency limitation remains below.
- Server album reads are not pinned to one database snapshot across the two queries; concurrent mutations can produce a temporarily inconsistent cover/photos pair. Next refresh converges; no permanent-loss path found in review. This is not silently claimed fixed.
- Existing deprecation warnings remain (Gradle/Compose APIs); deliberately corrupt-image negative tests log `incomplete input`. These are not hidden test failures.
- Delivery APK SHA-256: `6c5cd51dc96c8af5a9cf95bf62b4537ad4014a4b04b481f4032c07e6bfcf2371`.
- Delivery server SHA-256: `66052ac2cdc3e14bd95a705fe4bc591de3b0a308ce94ab3e23e540de7ed0453d`.
- Transfer TAR SHA-256: `0fa31ab30718ff7d88a613603d90a73f751857750dea894634c651fa57c3d852`.
