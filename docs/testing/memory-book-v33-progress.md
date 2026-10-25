# SDD ledger — plan: docs/superpowers/plans/2026-10-10-memory-book-integrity.md

Ruling: execute in existing dirty feature checkout, preserving all prior changes and not committing unrelated work — latest product implementation is uncommitted here; a clean worktree would omit it — cost: scoped diff review must distinguish existing work.
Ruling: continuous user authorization replaces repeated design approval gates — design stays within established warm handwritten product style — cost: user may request a visual revision.
Ruling: keep ZIP originals plus a photo-self-contained HTML, not inline large audio/video — avoids multi-hundred-MiB strings/heap pressure — cost: moving HTML alone does not carry playable original audio/video.
Browser audit: bootstrap failed twice (trusted Node process exited); no screenshot-based visual acceptance claim yet.
Preflight: compatible ZIP/pending names retained; LocalExportBuilder consumed by archive; Gradle/Maven serialized. Privacy ambiguity remains awaiting asynchronous reply.

Task 1: RED observed memory-book-v33-red.log (3 tests / 2 intentional failures: no readable inline photo, corrupt photo false success). GREEN memory-book-v33-green.log (3/3); native decoder warning belongs to malformed-input test.
Task 2: independent archive test first could not compile because RecordArchiveExporter did not exist; after adding implementation, it passed in local-export-v33-red.log. Snapshot race then reproduced HTML/JSON title mismatch.
Task 3: local-export-v33-red.log 13 tests / 9 failures: all 8 local-export safety/metadata tests plus snapshot race. Implementing sanitize and shared immutable snapshot.
Browser: retry after quota recovered still fails before tab creation. adb devices shows no attached device. No visual/device acceptance yet.
Ruling: full-space rejoin codes recover the authenticated issuer, not their partner — fixes identity takeover with existing session proof instead of heavyweight permission machinery — cost: a lost phone cannot be restored solely using the other person's pairing code; own existing session or owner bootstrap recovery is required.

2026-10-10 continuation:
- `safety-export-v33-green.log`: core database/network/sync full unit suites and 13 focused export tests passed (110 tests, zero failures/errors across current XML).
- `security-v33-red.log`: 9 tests failed, no errors; reproduces cross-member rejoin, deleted block replay and omitted export dates.
- `security-v33-green-and-date-red.log`: original 9 passed; the added malformed-date test failed as expected. Date validation now implemented, passed with the 3 export tests in media-readiness run.
- V14/V15 migration bytes match immutable V32 JAR; no editing old migrations or production checksum repair.
- `media-readiness-v33-red.log`: append 6 non-READY cases failed; avatar fixture used an overlong display name, causing 500 even for valid avatar. Correct fixture before relying on avatar RED.
- Read-only production HTTPS check: certificate verification succeeded, `/actuator/health` 404 and anonymous synthetic media request 401. No authorized production data, OSS ACL/RAM, or two-device test performed.
- Export review findings accepted: canonical media path boundary; whitelist all HTML/manifest fields; process-restored busy state; coroutine-result cancellation cleanup; avoid unnecessary Markdown/JSON copies. Tests and fixes in progress.
- Signing configuration unchanged. Auto-review rejected reusing debug signing for non-debug release; optional user question pending. No workaround performed.
- `avatar-validation-v33-red-corrected.log`: corrected short name fixture produced the expected 5 readiness/type failures, 4 legitimate/isolation controls passed. READY IMAGE filter implemented afterward.
- `memory-book-v33-server-full-final.log`: 187 tests passed, zero failures/errors/skips, executable V33 JAR packaged. First full run hit Windows Unix-domain temp-path loopback failure; fixed test runner environment and reran the complete suite, not an exclusion.
- `export-review-v33-red.log`: 10 tests/5 failures demonstrated nested fields, MUSIC name and private file path export. All 5 now pass in subsequent handoff run.
- `export-handoff-v33-red.log`: 19 export tests, only cancellation handoff failure; outer dispatcher-return cleanup restored afterward.
- `push-cancel-red-export-green-v33.log`: cancelled non-cooperative push APPLIED/CONFLICT/RETRYABLE response tests failed (3/6); exact-clock retry controls passed. Minimal post-response cancellation guard being applied before full Android verification.
- Full reviews distinguish cache-readable record archive from cloud backup. Remaining record JSON and transaction snapshot memory scale with record count; giant media are streamed, photo previews sampled and emitted one at a time.
- `memory-book-v33-android-full.log`: complete Android suite 363 tests, zero failures/errors/skips; app and instrumented APK built. Verified versionCode 33 and unchanged signing fingerprint.
- Fresh read-only review completed after fixing sandbox launch configuration. Important finding: instrumented MemoryBookVisualTest used a rejected cacheDir fixture; moved to filesDir/photos and adapted checks to lazy images. Actual device visual run remains unperformed.
- Non-blocking review limitation accepted: OS process kill can leave private export cache until system eviction. Follow-up orphan collection must preserve pending save-picker files. No claim of process-kill cache cleanup.
- Independent final scoped review: no remaining critical/important blockers after instrumented fixture/lazy-image changes; visual/device/deployment deliberately not certified.
- `visual-test-v33-build-final.log`: corrected instrumented APK and app build passed. Linux `sh -n` checked update script inside network-disabled container; transfer bundle contains only JAR, SHA256 and update script (no credentials).
- V33 artifacts created under releases/v33 and infra/local-runtime/cloud-transfer. Nothing uploaded to ECS, no APK installed, production cloud unchanged by this turn.
