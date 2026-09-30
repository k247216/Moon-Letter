# Self-Use M1 Reliable Recording Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Do not run tasks in parallel when they touch the same database contract. Check off each item only after its stated verification passes.

**Goal:** Deliver a genuinely usable Android-first two-person diary whose personal text records survive offline use and synchronize through a real Room → HTTP → PostgreSQL → change-feed → second Room chain, before expanding media or secondary features.

**Architecture:** Android renders only Room data. A local business write and its UUID v7 outbox operation commit together; a foreground/reconnect/manual sync engine sends typed operations and atomically applies pulled pages with their cursor. Spring Boot authenticates opaque device tokens, validates couple membership, applies each mutation and appends a per-couple ordered change in one PostgreSQL transaction. Shared entries contain author-owned, versioned perspectives rather than cross-author merging.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring Security, Maven, PostgreSQL, Flyway, Testcontainers or explicit test PostgreSQL; Kotlin, Android Gradle Plugin, Jetpack Compose, Room, WorkManager, Retrofit/OkHttp, Kotlinx Serialization.

**Authoritative spec:** `docs/superpowers/specs/2026-10-01-self-use-m1-design.md`

**Current truth:** M1 is `NOT VERIFIED`. Existing server and Android tests are component evidence only; `TwoDeviceSyncTest` and `TwoDeviceScenarioTest` are not accepted as end-to-end evidence.

---

## Execution rules

- Use test-driven development: write the named failing test, run it and record the expected failure, implement the smallest correct behavior, then rerun it.
- Do not start image work until Task 11 passes end to end.
- Do not start visual comparison until screens read real Room data.
- Every task ends with a focused commit and an evidence entry in `docs/testing/m1-acceptance.md`.
- Never commit `local.properties`, `.env`, tokens, bootstrap secrets, object-storage keys or `google-services.json`.
- A fake server may support unit tests but may not be labeled E2E.
- If a listed file already exists, modify it rather than creating a parallel implementation.

## Task 1: Make the server reproducibly bootable

**Files:**
- Modify: `server/pom.xml`
- Create: `server/src/main/resources/application.yml`
- Create: `server/src/main/resources/application-dev.yml`
- Modify: `server/src/test/resources/application-test.yml`
- Modify: `server/src/test/java/com/twomemory/app/CoupleDiaryApplicationTest.java`
- Modify: `README.md`

- [ ] Add a context test that supplies an explicit PostgreSQL URL and asserts Flyway has applied the expected schema.
- [ ] Run `cd server && mvn -Dtest=CoupleDiaryApplicationTest test`; preserve the initial failure caused by missing configuration.
- [ ] Define datasource, Flyway, actuator health and S3/notification defaults without embedding production secrets. The default profile must fail with a clear missing-database error instead of silently using an unrelated database.
- [ ] Start PostgreSQL using `docker compose -f infra/compose.yaml up -d postgres`, then run the context test and `mvn spring-boot:run -Dspring-boot.run.profiles=dev`.
- [ ] Record the health URL, command, exit code and commit in the acceptance record.
- [ ] Commit: `build(server): add reproducible runtime configuration`.

## Task 2: Replace trusted headers with device-session authentication

**Files:**
- Create: `server/src/main/resources/db/migration/V6__device_sessions_and_bootstrap.sql`
- Create: `server/src/main/java/com/twomemory/app/auth/DeviceSessionAuthenticationFilter.java`
- Create: `server/src/main/java/com/twomemory/app/auth/SecurityConfig.java`
- Create: `server/src/main/java/com/twomemory/app/auth/BootstrapController.java`
- Create: `server/src/main/java/com/twomemory/app/auth/BootstrapService.java`
- Create: `server/src/test/java/com/twomemory/app/auth/BootstrapAuthenticationTest.java`
- Modify: `server/src/main/java/com/twomemory/app/auth/AuthenticatedUser.java`
- Modify: `server/src/main/java/com/twomemory/app/couple/CoupleController.java`
- Modify: `server/src/main/java/com/twomemory/app/entry/EntryController.java`
- Modify: `server/src/main/java/com/twomemory/app/media/MediaController.java`
- Modify: `server/src/main/java/com/twomemory/app/sync/SyncController.java`

- [ ] Write real-database tests proving bootstrap works only on an empty installation with `BOOTSTRAP_SECRET`, a second bootstrap is rejected, an invalid bearer token receives 401, and a guessed `X-User-Id` grants no access.
- [ ] Add `device_session` with token hash, user/couple foreign keys, created/last-used/revoked timestamps; never persist plaintext tokens. Enforce at most one active session per member in M1 and rotate it on device replacement.
- [ ] Return an opaque token only when creating a session. Use `Authorization: Bearer`; disable form login and HTTP Basic; allow only health, bootstrap and pair endpoints explicitly.
- [ ] Remove production use of `AuthenticatedUser.fromHeader` and make controllers read the authenticated principal.
- [ ] Run `cd server && mvn -Dtest=BootstrapAuthenticationTest test`.
- [ ] Commit: `feat(auth): add bootstrap and device bearer sessions`.

## Task 3: Implement one-time partner pairing

**Files:**
- Create: `server/src/main/resources/db/migration/V7__secure_pairing_tokens.sql`
- Modify: `server/src/main/java/com/twomemory/app/couple/CoupleController.java`
- Modify: `server/src/main/java/com/twomemory/app/couple/CoupleService.java`
- Modify: `server/src/main/java/com/twomemory/app/couple/CoupleDtos.java`
- Modify: `server/src/test/java/com/twomemory/app/couple/CoupleApiTest.java`

- [ ] Add tests for a cryptographically random token, 15-minute expiry, single use, two-member maximum and token non-disclosure after creation.
- [ ] Store only the token hash. Generate at least 128 bits with `SecureRandom`; revoke on successful pairing and on replacement.
- [ ] Make pairing create the second member and its first device session transactionally.
- [ ] Delete or migrate the old enumerable pairing-code path; do not keep two active mechanisms.
- [ ] Run `cd server && mvn -Dtest=CoupleApiTest test`.
- [ ] Commit: `feat(couple): secure one-time partner pairing`.

## Task 4: Enforce actor ownership in every business operation

**Files:**
- Modify: `server/src/main/java/com/twomemory/app/entry/EntryController.java`
- Modify: `server/src/main/java/com/twomemory/app/entry/EntryService.java`
- Modify: `server/src/main/java/com/twomemory/app/entry/CommentService.java`
- Modify: `server/src/main/java/com/twomemory/app/media/MediaController.java`
- Modify: `server/src/main/java/com/twomemory/app/media/MediaService.java`
- Modify: `server/src/test/java/com/twomemory/app/entry/EntryApiTest.java`
- Modify: `server/src/test/java/com/twomemory/app/media/MediaServiceTest.java`

- [ ] Add negative tests for reading another space, publishing another user's draft, editing another author's shared block, modifying another profile and referencing non-ready or foreign media.
- [ ] Pass the authenticated actor into every service mutation; remove nullable-user overloads and controller paths that discard identity.
- [ ] Define the shared-entry rule directly in service code: one author owns each perspective block; the partner may append their own block but cannot update the first author's block.
- [ ] Call `MediaService.requireReady` for every media reference at publication.
- [ ] Run the focused entry/media tests, then `cd server && mvn test`.
- [ ] Commit: `fix(server): enforce actor and media authorization`.

## Task 5: Make mutations typed, idempotent and transactionally observable

**Files:**
- Create: `server/src/main/java/com/twomemory/app/sync/SyncOperationDispatcher.java`
- Modify: `server/src/main/java/com/twomemory/app/sync/SyncController.java`
- Modify: `server/src/main/java/com/twomemory/app/sync/SyncDtos.java`
- Modify: `server/src/main/java/com/twomemory/app/sync/IdempotencyService.java`
- Modify: `server/src/main/java/com/twomemory/app/entry/EntryService.java`
- Modify: `server/src/test/java/com/twomemory/app/sync/SyncApiTest.java`

- [ ] Write tests showing a typed `CREATE_PERSONAL_ENTRY` changes the entry tables, a duplicate `(couple_id, operation_id)` has one visible effect and returns the saved response, and the same ID with different payload is rejected.
- [ ] Replace arbitrary client-supplied change payload insertion with a closed operation-type dispatcher and validated DTOs.
- [ ] Wrap idempotency claim, business mutation, revision creation, change append and response storage in one `@Transactional` boundary.
- [ ] Prove an injected failure before commit leaves neither business data nor change rows nor a completed idempotency response.
- [ ] Run `cd server && mvn -Dtest=SyncApiTest test`.
- [ ] Commit: `feat(sync): dispatch typed idempotent operations`.

## Task 6: Replace the unsafe global cursor with a per-couple ordered feed

**Files:**
- Create: `server/src/main/resources/db/migration/V8__per_couple_change_sequence.sql`
- Modify: `server/src/main/java/com/twomemory/app/sync/ChangeFeedService.java`
- Modify: `server/src/main/java/com/twomemory/app/sync/SyncDtos.java`
- Modify: `server/src/test/java/com/twomemory/app/db/SchemaConstraintTest.java`
- Create: `server/src/test/java/com/twomemory/app/sync/ChangeFeedOrderingTest.java`

- [ ] Add a concurrency test that holds transaction A, commits B, then releases A; prove a client cannot advance past an unseen committed change.
- [ ] Add `couple_sync_state` and `(couple_id, space_sequence)` uniqueness. Lock the couple state row while allocating the next sequence inside the mutation transaction.
- [ ] Return pages ordered by `space_sequence` with `next_cursor` equal to the highest fully returned sequence and an explicit `has_more`.
- [ ] Remove reliance on a global identity as a client cursor.
- [ ] Run the ordering and schema tests repeatedly, then the full server suite.
- [ ] Commit: `fix(sync): serialize per-couple change sequences`.

## Task 7: Add a real server recording-loop E2E test

**Files:**
- Create: `server/src/test/java/com/twomemory/app/e2e/SelfUseRecordingLoopE2ETest.java`
- Modify or rename: `server/src/test/java/com/twomemory/app/e2e/TwoDeviceSyncTest.java`
- Modify: `server/pom.xml`
- Modify: `docs/testing/m1-acceptance.md`

- [ ] Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with real PostgreSQL via Testcontainers or an explicit isolated `TEST_DB_URL`; send real HTTP requests with two bearer tokens.
- [ ] Cover bootstrap, pairing, device A personal draft/publish, device B pull, duplicate retry, unauthorized third token, shared dual perspectives, pagination and process-level server restart where feasible.
- [ ] Rename the existing `FakeServer` test to identify it as a model/unit test, or remove it if redundant. It must not appear in the E2E count.
- [ ] Run `cd server && mvn -Dtest=SelfUseRecordingLoopE2ETest test`, then `mvn test`.
- [ ] Record database type, command, test count, exit code and commit.
- [ ] Commit: `test(server): add real HTTP PostgreSQL recording loop`.

## Task 8: Establish a compiling Android and instrumented-test foundation

**Files:**
- Modify: `android/build.gradle.kts`
- Modify: `android/gradle/libs.versions.toml`
- Modify: every Android module `build.gradle.kts` that uses Compose or instrumentation
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/feature/timeline/src/main/java/com/twomemory/timeline/TimelineScreen.kt`
- Create: `android/gradle.properties`

- [ ] Run `cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest`; keep the first compiler/configuration failures as evidence.
- [ ] Enable Compose where Compose code exists, configure the instrumentation runner and dependencies, fix incorrect imports, align JVM targets and configure JUnit consistently.
- [ ] Run `./gradlew :app:assembleDebug testDebugUnitTest` and one minimal `connectedDebugAndroidTest` on a named API 37 device.
- [ ] Document JDK, SDK, emulator/device model and Android version; do not commit `local.properties`.
- [ ] Commit: `build(android): establish verified app and test baseline`.

## Task 9: Implement Room store and persistent outbox first

**Files:**
- Modify: `android/core/database/src/main/java/com/twomemory/database/AppDatabase.kt`
- Modify: `android/core/database/src/main/java/com/twomemory/database/EntryDao.kt`
- Modify: `android/core/database/src/main/java/com/twomemory/database/OutboxDao.kt`
- Modify: `android/core/database/src/main/java/com/twomemory/database/LocalEntryWriter.kt`
- Create: `android/core/database/src/main/java/com/twomemory/database/RoomSyncStore.kt`
- Modify: `android/core/database/src/androidTest/java/com/twomemory/database/LocalEntryWriterTest.kt`
- Create: `android/core/database/src/androidTest/java/com/twomemory/database/RoomSyncStoreTest.kt`

- [ ] Test that entry plus outbox commit atomically, a killed/reopened database retains pending operations, duplicate pulled changes are harmless, and applying a page plus cursor is one transaction.
- [ ] Implement all `SyncStore` behavior against DAOs; remove in-memory production stores and constant cursor values.
- [ ] Use two separate temporary Room database files in instrumentation tests to represent two devices.
- [ ] Run `cd android && ./gradlew :core:database:connectedDebugAndroidTest`.
- [ ] Commit: `feat(android): persist entries outbox and sync cursor in Room`.

## Task 10: Connect Retrofit and the synchronization engine

**Files:**
- Modify: `android/core/network/src/main/java/com/twomemory/network/CoupleDiaryApi.kt`
- Create: `android/core/network/src/main/java/com/twomemory/network/RetrofitFactory.kt`
- Modify: `android/core/sync/src/main/java/com/twomemory/sync/SyncEngine.kt`
- Modify: `android/core/sync/src/main/java/com/twomemory/sync/SyncWorker.kt`
- Modify: `android/core/sync/src/test/java/com/twomemory/sync/SyncEngineTest.kt`
- Modify: `android/app/src/main/java/com/twomemory/app/TwoMemoryApp.kt`
- Modify: `android/app/src/main/java/com/twomemory/app/AppNavigation.kt`

- [ ] Add tests for FIFO push, saved idempotency ID reuse after timeout, pull-until-`has_more=false`, atomic cursor updates, exponential retry and 401 stopping with a re-pair message.
- [ ] Implement bearer injection, typed serialization, timeouts and error mapping with Retrofit/OkHttp.
- [ ] Wire the real `RoomSyncStore`, API and worker in the application module. Trigger sync on app start, foreground return, manual refresh and network recovery.
- [ ] Keep background WorkManager as best effort; do not claim one-minute delivery and do not make FCM a dependency.
- [ ] Run `cd android && ./gradlew :core:sync:testDebugUnitTest :app:assembleDebug`.
- [ ] Commit: `feat(android): wire Room Retrofit synchronization`.

## Task 11: Prove the personal-text two-device vertical slice

**Files:**
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/EditorViewModel.kt`
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/PersonalEditorScreen.kt`
- Modify: `android/feature/timeline/src/main/java/com/twomemory/timeline/TimelineViewModel.kt`
- Modify: `android/feature/timeline/src/main/java/com/twomemory/timeline/TimelineScreen.kt`
- Modify: `android/core/sync/src/test/java/com/twomemory/sync/TwoDeviceScenarioTest.kt`
- Create: `android/app/src/androidTest/java/com/twomemory/app/TwoDeviceRecordingLoopTest.kt`

- [ ] Remove hardcoded production timeline/editor data. Create and edit through Room; observe timeline from Room only.
- [ ] Build an instrumented harness with two independent Room databases and the real HTTP server. Device A writes while offline, reconnects and pushes; device B pulls and exposes the exact entry from its own database.
- [ ] Cover reverse reconnect, duplicate timeout retry and app-process recreation. Replace the current identical-string assertion; retain it only if renamed as a serialization unit test.
- [ ] Run the real server, then `./gradlew :app:connectedDebugAndroidTest` and preserve logs.
- [ ] Gate: do not begin Tasks 12–13 until this task is green.
- [ ] Commit: `feat(m1): complete personal text two-device slice`.

## Task 12: Add shared perspectives, comments and version history

**Files:**
- Modify: `android/core/model/src/main/java/com/twomemory/model/EntryModels.kt`
- Modify: `android/core/database/src/main/java/com/twomemory/database/AppDatabase.kt`
- Modify: `android/core/database/src/main/java/com/twomemory/database/EntryDao.kt`
- Create: `android/core/database/src/main/java/com/twomemory/database/CommentDao.kt`
- Create: `android/core/database/src/main/java/com/twomemory/database/RevisionDao.kt`
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/SharedEditorScreen.kt`
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/EditorViewModel.kt`
- Modify: `server/src/test/java/com/twomemory/app/entry/EntryServiceTest.java`
- Modify: `android/feature/editor/src/androidTest/java/com/twomemory/editor/EditorScreenTest.kt`

- [ ] Test that each perspective displays author and exact time, either partner can append their own view, neither can edit the other's blocks, and every published edit creates a viewable revision.
- [ ] Add comments with author and precise time; allow deletion only by the comment author.
- [ ] Do not implement CRDT, live cursor, cross-author merge or conflict-resolution UI.
- [ ] Run server tests plus Android unit/instrumented tests for the shared path.
- [ ] Commit: `feat(entries): add author-owned shared perspectives`.

## Task 13: Add image lifecycle, backup/restore and selective export

**Files:**
- Modify: `server/src/main/java/com/twomemory/app/media/MediaController.java`
- Modify: `server/src/main/java/com/twomemory/app/media/MediaService.java`
- Modify: `server/src/main/java/com/twomemory/app/media/ObjectStorage.java`
- Modify: `server/src/main/java/com/twomemory/app/media/S3ObjectStorage.java`
- Modify: `android/core/model/src/main/java/com/twomemory/model/EntryModels.kt`
- Modify: `android/core/database/src/main/java/com/twomemory/database/AppDatabase.kt`
- Modify: `android/core/network/src/main/java/com/twomemory/network/CoupleDiaryApi.kt`
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/EditorViewModel.kt`
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/PersonalEditorScreen.kt`
- Create: `scripts/backup.sh`
- Create: `scripts/restore.sh`
- Create: `server/src/main/java/com/twomemory/app/export/ExportController.java`
- Create: `server/src/main/java/com/twomemory/app/export/ExportService.java`
- Create: `server/src/test/java/com/twomemory/app/export/ExportServiceTest.java`
- Create: `docs/testing/m1-backup-restore.md`

- [ ] Test pending/uploaded/ready/failure states and reject publication for non-ready or foreign media.
- [ ] Make retries reuse an object identity; preserve a ready upload when publication fails.
- [ ] Produce a versioned backup manifest and perform a restore into an isolated database/bucket; compare counts and sampled hashes.
- [ ] Export a selected record and a date range as JSON, Markdown/HTML and original media without secrets.
- [ ] Run media/export tests and the documented restore drill.
- [ ] Commit: `feat(data): add image integrity backup and export`.

## Task 14: Complete real-data UI and final acceptance

**Files:**
- Modify: `android/feature/timeline/`, `android/feature/editor/`, `android/feature/couple/`, `android/core/designsystem/`
- Modify: `android/app/src/main/java/com/twomemory/app/AppNavigation.kt`
- Modify: `docs/testing/m1-acceptance.md`
- Modify: `README.md`

- [ ] Wire profile name/avatar and per-device beige/white theme. Keep album and map as honest unavailable states.
- [ ] Capture 390 × 844 reference-state screenshots for timeline, personal editor, shared editor and couple profile; also test a shorter viewport, keyboard open and enlarged font.
- [ ] Compare against `docs/design/reference/` for structure, spacing, color, icon stroke, typography, scroll and navigation. Do not use screenshots as UI backgrounds.
- [ ] Run the full server suite, Android unit tests, connected tests, assembly, two-device scenario and backup/restore drill from a clean checkout.
- [ ] On two real devices or two independent emulator data directories, record a complete screen capture of offline A → online server → B Room/UI.
- [ ] Update acceptance rows with exact commands, environment, commit, exit code and artifact paths. Any missing mandatory row leaves status `NOT VERIFIED`.
- [ ] Commit: `test(m1): record verified self-use acceptance evidence`.

---

## Completion boundary

Do not claim M1 complete because code compiles, 28 tests pass, a fake two-device model passes, or screenshots resemble the references. Completion requires the Task 14 evidence set on one exact commit. Video, audio, music, map stories, album organization, countdowns, capsules, “过去的今天” and weekly summaries remain subsequent milestones.
