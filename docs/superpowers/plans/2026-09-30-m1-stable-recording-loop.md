# M1 Stable Recording Loop Implementation Plan

> **停止执行。** 本计划已由 `docs/superpowers/plans/2026-10-01-self-use-m1-implementation.md` 取代。旧计划中的跨作者冲突合并、FCM 时效和验收口径不再属于 M1 执行基线。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android-first, offline-capable two-person recording loop in which either device can create personal or shared entries, synchronize without duplication, preserve edit history, and render the approved home/editor/profile designs.

**Architecture:** The Android UI reads only from Room and records writes in the same transaction as a persistent outbox; WorkManager pushes those operations and pulls server changes by monotonic cursor. A Spring Boot service backed by PostgreSQL is authoritative for membership, validation, idempotency, versions, and conflicts; image objects use an S3-compatible store and become publishable only after server confirmation.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Maven, PostgreSQL 18, Flyway, Testcontainers; Kotlin 2.4.10, Android Gradle Plugin 9.4.0, compileSdk 37, minSdk 26, Jetpack Compose BOM 2026.09.00, Room 2.8.4, WorkManager 2.11.1, Retrofit/OkHttp, Kotlinx Serialization.

**Spec:** `docs/superpowers/specs/2026-09-30-couple-diary-design.md`

## Global Constraints

- The six files in `docs/design/reference/` are authoritative visual targets; implementation may not use a full reference screenshot as a background.
- The Android base state is 390 × 844, but layouts must reflow on other Android sizes.
- The fixed navigation order is `时光 / 相册 / ＋记录 / 地图 / 我们`; editors do not show app navigation.
- Room is the Android UI's only business-data read source.
- Every local mutation and its outbox operation commit in one Room transaction.
- Every server mutation is scoped by authenticated user plus `couple_id` and is idempotent by UUID v7 `operation_id`.
- API timestamps are ISO-8601 UTC; occurrence timezone is a separate IANA identifier.
- Shared edits are asynchronous and revisioned; no WebSocket, live cursor, or CRDT is introduced.
- Location is a one-shot snapshot or manual city selection; no background location or track table exists.
- Functional icons use one uniform thin-outline family; warm beige and pure white share geometry and spacing.
- User-visible Chinese copy in the approved mockups remains unchanged unless a test exposes a functional ambiguity.

## Review Focus

- Two offline devices edit the same block: neither edit is silently overwritten, and conflict resolution creates a new revision (Task 4 tests).
- A process dies after Room commit but before HTTP success: the outbox operation resumes and the server applies it exactly once (Tasks 5 and 7 tests).
- A user guesses another space's resource UUID: every read and write returns forbidden/not-found without leaking content (Tasks 3–5 tests).
- Image upload completes but entry publication fails: the draft and ready media remain recoverable without duplicate object creation (Tasks 6 and 7 tests).
- Font scaling, keyboard insets, and a shorter Android viewport do not hide publish or navigation controls (Tasks 8 and 9 UI tests).

---

## File Structure

```text
android/
  app/                         application, root navigation, dependency wiring
  core/model/                  shared Android domain models and enums
  core/database/               Room entities, DAOs, transactions, migrations
  core/network/                API DTOs and Retrofit service
  core/sync/                   outbox processor, pull sync, WorkManager worker
  core/designsystem/           tokens, themes, icons, paper surfaces, shared UI
  feature/timeline/            home timeline screen and ViewModel
  feature/editor/              personal/shared editors and ViewModels
  feature/couple/              profile, theme, names, avatars
server/
  pom.xml                      dependency and build definition
  src/main/java/.../auth/      simple session principal and pair authorization
  src/main/java/.../couple/    space/member/profile domain
  src/main/java/.../entry/     entries, blocks, revisions, comments
  src/main/java/.../sync/      idempotency and monotonic change feed
  src/main/java/.../media/     image upload lifecycle
  src/main/resources/db/migration/  PostgreSQL schema
infra/
  compose.yaml                 PostgreSQL and MinIO for local development
```

### Task 1: Reproducible Project Foundations

**Files:**
- Create: `.gitignore`
- Create: `README.md`
- Create: `infra/compose.yaml`
- Create: `server/pom.xml`
- Create: `server/src/main/java/com/twomemory/app/CoupleDiaryApplication.java`
- Create: `server/src/test/java/com/twomemory/app/CoupleDiaryApplicationTest.java`
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle/libs.versions.toml`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/com/twomemory/app/MainActivity.kt`

**Interfaces:**
- Consumes: Java 21 and Maven 3.9; Android SDK 37 is an explicit local prerequisite.
- Produces: `server` Maven module, `android` Gradle build, PostgreSQL/MinIO local services, and documented one-command verification entry points.

- [ ] **Step 1: Write the failing server context test**

Create `CoupleDiaryApplicationTest.contextLoads()` using `@SpringBootTest`; it must assert that the application context starts with the test profile.

- [ ] **Step 2: Run the server test and verify it fails before the project exists**

Run: `cd server && mvn test -Dtest=CoupleDiaryApplicationTest`

Expected: FAIL because no Maven project/application class exists.

- [ ] **Step 3: Create the minimal Spring Boot application and local infrastructure**

Pin Spring Boot `3.5.16`, Java release `21`, PostgreSQL, Flyway, Validation, Security, Testcontainers, and Spring Boot Test. Define PostgreSQL 18 and MinIO services with named volumes and health checks in `infra/compose.yaml`.

- [ ] **Step 4: Create the Android build shell**

Pin AGP `9.4.0`, Kotlin `2.4.10`, Compose BOM `2026.09.00`, compileSdk `37`, minSdk `26`, and JVM target `17`. `MainActivity` renders the text `TwoMemory bootstrap` only; no product UI is implemented in this task.

- [ ] **Step 5: Verify both build shells**

Run: `cd server && mvn test`

Expected: PASS.

Run: `cd android && ./gradlew :app:assembleDebug`

Expected: PASS when Android SDK 37 is installed; if the SDK is absent, record the prerequisite and continue only with server tasks until it is installed.

- [ ] **Step 6: Commit**

```bash
git add .gitignore README.md infra server android
git commit -m "build: bootstrap Android and server projects"
```

### Task 2: PostgreSQL Schema and Constraint Contract

**Files:**
- Create: `server/src/main/resources/db/migration/V1__identity_and_entries.sql`
- Create: `server/src/main/resources/db/migration/V2__sync_and_media.sql`
- Create: `server/src/test/java/com/twomemory/app/db/SchemaConstraintTest.java`
- Create: `server/src/test/resources/application-test.yml`

**Interfaces:**
- Consumes: PostgreSQL datasource from Task 1.
- Produces: the exact tables/enums/constraints described by the spec and a `PostgreSQLContainer<?>` integration-test base.

- [ ] **Step 1: Write failing schema tests**

Add tests named `activeSpaceRejectsThirdMember`, `lunarAnniversaryAcceptsMonthEightDayFifteen`, `mediaRejectsNonPositiveSize`, `revisionNumberIsUniquePerEntry`, and `locationRejectsOutOfRangeCoordinates`. Each test executes real SQL against Testcontainers and asserts the named constraint rejects invalid input.

- [ ] **Step 2: Run the constraint suite and verify failure**

Run: `cd server && mvn test -Dtest=SchemaConstraintTest`

Expected: FAIL because migrations/tables do not exist.

- [ ] **Step 3: Implement migrations**

Create all M1 tables from spec sections 5.1–5.3 plus `location_snapshot`, `anniversary`, `time_capsule`, `idempotency_record`, and `sync_change`. Use native PostgreSQL checks for scalar ranges and a trigger/function for the two-active-member limit.

- [ ] **Step 4: Verify migrations and constraints**

Run: `cd server && mvn test -Dtest=SchemaConstraintTest`

Expected: PASS with a clean container migration.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/resources/db server/src/test
git commit -m "feat: define relational data constraints"
```

### Task 3: Couple Space and Editable Profiles

**Files:**
- Create: `server/src/main/java/com/twomemory/app/couple/CoupleService.java`
- Create: `server/src/main/java/com/twomemory/app/couple/CoupleController.java`
- Create: `server/src/main/java/com/twomemory/app/couple/CoupleDtos.java`
- Create: `server/src/main/java/com/twomemory/app/auth/AuthenticatedUser.java`
- Create: `server/src/main/java/com/twomemory/app/auth/SpaceAccessPolicy.java`
- Test: `server/src/test/java/com/twomemory/app/couple/CoupleApiTest.java`

**Interfaces:**
- Consumes: schema from Task 2.
- Produces: `CoupleView CoupleService.createSpace(UUID ownerId)`, `PairResult pair(UUID userId, String oneTimeCode)`, `ProfileView updateProfile(UUID userId, UpdateProfileRequest request)`, and authenticated `/api/v1/couple` endpoints.

- [ ] **Step 1: Write failing API tests**

Test exact cases: owner creates a space; a valid one-time code pairs one partner and becomes unusable; a third user is rejected; nickname trims whitespace and enforces 1–24 Unicode code points; a member cannot update the other member's profile; theme values are only `WARM_BEIGE` and `PURE_WHITE`.

- [ ] **Step 2: Run and verify failure**

Run: `cd server && mvn test -Dtest=CoupleApiTest`

Expected: FAIL because controller/service are absent.

- [ ] **Step 3: Implement the service and authorization boundary**

Use `SpaceAccessPolicy.requireMember(UUID userId, UUID coupleId)`. Pairing locks the target space row within one transaction and stores only a hash of the one-time code.

- [ ] **Step 4: Verify API and cross-space isolation**

Run: `cd server && mvn test -Dtest=CoupleApiTest`

Expected: PASS, including guessed-resource authorization tests.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/twomemory/app/auth server/src/main/java/com/twomemory/app/couple server/src/test/java/com/twomemory/app/couple
git commit -m "feat: add two-person spaces and profiles"
```

### Task 4: Entries, Blocks, Revisions, and Conflict Resolution

**Files:**
- Create: `server/src/main/java/com/twomemory/app/entry/EntryService.java`
- Create: `server/src/main/java/com/twomemory/app/entry/EntryController.java`
- Create: `server/src/main/java/com/twomemory/app/entry/EntryDtos.java`
- Create: `server/src/main/java/com/twomemory/app/entry/EntryConflict.java`
- Create: `server/src/main/java/com/twomemory/app/entry/CommentService.java`
- Test: `server/src/test/java/com/twomemory/app/entry/EntryServiceTest.java`
- Test: `server/src/test/java/com/twomemory/app/entry/EntryApiTest.java`

**Interfaces:**
- Consumes: `SpaceAccessPolicy.requireMember` from Task 3.
- Produces: `EntryView createDraft(CreateEntryCommand command)`, `PublishResult publish(UUID entryId, long baseVersion)`, `ApplyChangesResult applyChanges(UUID entryId, int baseRevision, List<BlockMutation> mutations)`, `EntryView resolveConflict(UUID entryId, ResolveConflictCommand command)`, `CommentView addComment(UUID entryId, UUID authorId, String body, UUID replyToId)`, and `TimelinePage readTimeline(UUID coupleId, TimelineCursor cursor, int limit)`.

- [ ] **Step 1: Write failing domain tests**

Cover personal draft privacy, collaborative maximum-two contributors, 1–20000-code-point text, stable block ordering, publish revision creation, revision immutability, different-block auto-merge, same-block `409` conflict, delete-versus-edit conflict, conflict resolution producing a new revision, 1–2000-code-point comments, and replies being restricted to the same entry.

- [ ] **Step 2: Run and verify failure**

Run: `cd server && mvn test -Dtest=EntryServiceTest,EntryApiTest`

Expected: FAIL because entry services are absent.

- [ ] **Step 3: Implement transaction boundaries and optimistic locking**

`applyChanges` locks the entry row, compares `baseRevision`, groups mutations by block UUID, and only auto-merges when changed block IDs do not overlap. Every successful published mutation appends a complete `entry_revision.snapshot`.

- [ ] **Step 4: Verify version and security behavior**

Run: `cd server && mvn test -Dtest=EntryServiceTest,EntryApiTest`

Expected: PASS with no test updating an existing revision row.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/twomemory/app/entry server/src/test/java/com/twomemory/app/entry
git commit -m "feat: add revisioned personal and shared entries"
```

### Task 5: Idempotent Incremental Sync

**Files:**
- Create: `server/src/main/java/com/twomemory/app/sync/IdempotencyService.java`
- Create: `server/src/main/java/com/twomemory/app/sync/ChangeFeedService.java`
- Create: `server/src/main/java/com/twomemory/app/sync/SyncController.java`
- Create: `server/src/main/java/com/twomemory/app/sync/SyncNotificationPublisher.java`
- Create: `server/src/main/java/com/twomemory/app/sync/FcmSyncNotificationPublisher.java`
- Test: `server/src/test/java/com/twomemory/app/sync/SyncApiTest.java`

**Interfaces:**
- Consumes: couple authorization and entry mutations from Tasks 3–4.
- Produces: `MutationResult executeOnce(UUID operationId, UUID userId, String payloadHash, Supplier<MutationResult> mutation)`, `ChangePage readChanges(UUID coupleId, long after, int limit)`, and `void notifySpaceChanged(UUID coupleId, long latestSequence)` exposed by `/api/v1/sync/operations` and `/api/v1/sync/changes`.

- [ ] **Step 1: Write failing sync tests**

Assert duplicate `operation_id` returns the original result, a reused ID with different payload is rejected, change sequence is monotonic, a page limit above 200 is rejected, tombstones are returned, cursors do not advance on transaction rollback, and one space cannot read another space's feed.

- [ ] **Step 2: Run and verify failure**

Run: `cd server && mvn test -Dtest=SyncApiTest`

Expected: FAIL because sync services are absent.

- [ ] **Step 3: Implement idempotency and change feed in the mutation transaction**

Hash the canonical request payload with SHA-256. Store operation result status/body and append `sync_change` before commit. After commit, send an FCM data message containing only `couple_id` and `latest_sequence`; no record body, media URL, coordinate, or author text may enter the notification payload. Tests use an in-memory publisher.

- [ ] **Step 4: Verify exactly-once visible effects**

Run: `cd server && mvn test -Dtest=SyncApiTest`

Expected: PASS, including simulated client timeout followed by retry.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/twomemory/app/sync server/src/test/java/com/twomemory/app/sync
git commit -m "feat: add idempotent incremental sync"
```

### Task 6: Recoverable Image Upload Lifecycle

**Files:**
- Create: `server/src/main/java/com/twomemory/app/media/MediaService.java`
- Create: `server/src/main/java/com/twomemory/app/media/MediaController.java`
- Create: `server/src/main/java/com/twomemory/app/media/ObjectStorage.java`
- Create: `server/src/main/java/com/twomemory/app/media/S3ObjectStorage.java`
- Test: `server/src/test/java/com/twomemory/app/media/MediaServiceTest.java`

**Interfaces:**
- Consumes: space authorization from Task 3.
- Produces: `UploadTicket createUpload(CreateMediaCommand command)`, `MediaAssetView completeUpload(UUID assetId, String sha256)`, `MediaAssetView markFailed(UUID assetId, String code)`, and `void requireReady(UUID coupleId, Collection<UUID> assetIds)`.

- [ ] **Step 1: Write failing lifecycle tests**

Cover MIME whitelist, 20 MiB image limit, positive dimensions, SHA-256 verification, retry returning the existing asset for the same operation, another space being unable to complete an upload, and publication failure leaving both draft and `READY` media recoverable.

- [ ] **Step 2: Run and verify failure**

Run: `cd server && mvn test -Dtest=MediaServiceTest`

Expected: FAIL because media services are absent.

- [ ] **Step 3: Implement S3-compatible presigned upload flow**

Server generates object keys; clients never choose final keys. `completeUpload` verifies object metadata/checksum before transitioning `UPLOADING -> PROCESSING -> READY` for M1 images.

- [ ] **Step 4: Verify lifecycle**

Run: `cd server && mvn test -Dtest=MediaServiceTest`

Expected: PASS with no path allowing an entry to reference non-`READY` media.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/twomemory/app/media server/src/test/java/com/twomemory/app/media
git commit -m "feat: add recoverable image uploads"
```

### Task 7: Android Room, Outbox, and Sync Worker

**Files:**
- Create: `android/core/model/src/main/java/com/twomemory/model/EntryModels.kt`
- Create: `android/core/database/src/main/java/com/twomemory/database/AppDatabase.kt`
- Create: `android/core/database/src/main/java/com/twomemory/database/EntryDao.kt`
- Create: `android/core/database/src/main/java/com/twomemory/database/OutboxDao.kt`
- Create: `android/core/database/src/main/java/com/twomemory/database/LocalEntryWriter.kt`
- Create: `android/core/network/src/main/java/com/twomemory/network/CoupleDiaryApi.kt`
- Create: `android/core/sync/src/main/java/com/twomemory/sync/SyncEngine.kt`
- Create: `android/core/sync/src/main/java/com/twomemory/sync/SyncWorker.kt`
- Create: `android/core/sync/src/main/java/com/twomemory/sync/SyncMessagingService.kt`
- Test: `android/core/database/src/androidTest/java/com/twomemory/database/LocalEntryWriterTest.kt`
- Test: `android/core/sync/src/test/java/com/twomemory/sync/SyncEngineTest.kt`

**Interfaces:**
- Consumes: Task 5 sync DTO contract.
- Produces: `suspend fun LocalEntryWriter.save(command: LocalEntryCommand): UUID`, `suspend fun SyncEngine.pushPending(): SyncResult`, `suspend fun SyncEngine.pullAfter(cursor: Long): SyncResult`, and `fun EntryDao.observeTimeline(): Flow<PagingData<TimelineItem>>`.

- [ ] **Step 1: Write failing Room transaction tests**

Assert entry plus outbox are atomic, process death leaves `PENDING` operations persisted, failed writes leave neither row, and UUID/timezone/code-point fields round-trip exactly.

- [ ] **Step 2: Write failing sync-engine tests with a fake API**

Assert FIFO operation processing, exponential-retry state, duplicate success removal, conflict preservation, no cursor advance when Room apply fails, and a killed/restarted engine resuming the same operation ID.

- [ ] **Step 3: Run and verify failure**

Run: `cd android && ./gradlew :core:database:connectedDebugAndroidTest :core:sync:test`

Expected: FAIL because database/sync implementations are absent.

- [ ] **Step 4: Implement Room and sync state machine**

Use a single `@Transaction` writer. `SyncWorker` is unique work with `ExistingWorkPolicy.KEEP`; it delegates ordering and cursor decisions to `SyncEngine`. `SyncMessagingService` validates the space identifier and only enqueues that same unique worker.

- [ ] **Step 5: Verify offline recovery behavior**

Run: `cd android && ./gradlew :core:database:connectedDebugAndroidTest :core:sync:test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/core
git commit -m "feat: add offline outbox synchronization"
```

### Task 8: Android Design System and Personal/Shared Editors

**Files:**
- Create: `android/core/designsystem/src/main/java/com/twomemory/designsystem/ColorTokens.kt`
- Create: `android/core/designsystem/src/main/java/com/twomemory/designsystem/TypeTokens.kt`
- Create: `android/core/designsystem/src/main/java/com/twomemory/designsystem/TwoMemoryTheme.kt`
- Create: `android/core/designsystem/src/main/java/com/twomemory/designsystem/PaperSurface.kt`
- Create: `android/core/designsystem/src/main/java/com/twomemory/designsystem/TwoMemoryIcons.kt`
- Create: `android/feature/editor/src/main/java/com/twomemory/editor/PersonalEditorScreen.kt`
- Create: `android/feature/editor/src/main/java/com/twomemory/editor/SharedEditorScreen.kt`
- Create: `android/feature/editor/src/main/java/com/twomemory/editor/EditorViewModel.kt`
- Test: `android/feature/editor/src/androidTest/java/com/twomemory/editor/EditorScreenTest.kt`

**Interfaces:**
- Consumes: `LocalEntryWriter.save` and M1 image-upload states.
- Produces: `PersonalEditorRoute`, `SharedEditorRoute`, `EditorUiState`, and shared attachment/time/location controls.

- [ ] **Step 1: Write failing editor UI tests**

Assert personal mode opens directly into the text field, exact occurrence time `2026年9月30日 · 20:18` is editable, toolbar order is `图片/视频/语音/音乐/城市/更多`, shared blocks expose author labels, publish remains reachable with IME and 200% font scale, and a 390 × 700 viewport can scroll to every action.

- [ ] **Step 2: Run and verify failure**

Run: `cd android && ./gradlew :feature:editor:connectedDebugAndroidTest`

Expected: FAIL because screens are absent.

- [ ] **Step 3: Implement tokens and reusable controls**

Implement warm beige and pure white themes with identical dimensions. `TwoMemoryIcons` wraps one outline icon family and forbids per-screen icon substitution.

- [ ] **Step 4: Implement both editors against approved references**

Personal editor follows `personal-entry-editor.png`; shared editor follows `shared-entry-editor.png`. Use real editable Compose elements, lazy/scrollable content, and content blocks rather than rasterized reference sections.

- [ ] **Step 5: Verify behavior and capture baseline screenshots**

Run: `cd android && ./gradlew :feature:editor:connectedDebugAndroidTest`

Expected: PASS and screenshots are produced for 390 × 844 warm beige plus pure white smoke state.

- [ ] **Step 6: Commit**

```bash
git add android/core/designsystem android/feature/editor
git commit -m "feat: build personal and shared editors"
```

### Task 9: Timeline, Couple Page, and Root Navigation

**Files:**
- Create: `android/app/src/main/java/com/twomemory/app/TwoMemoryApp.kt`
- Create: `android/app/src/main/java/com/twomemory/app/AppNavigation.kt`
- Create: `android/core/designsystem/src/main/java/com/twomemory/designsystem/BottomNavigation.kt`
- Create: `android/feature/timeline/src/main/java/com/twomemory/timeline/TimelineScreen.kt`
- Create: `android/feature/timeline/src/main/java/com/twomemory/timeline/TimelineViewModel.kt`
- Create: `android/feature/couple/src/main/java/com/twomemory/couple/CoupleScreen.kt`
- Create: `android/feature/couple/src/main/java/com/twomemory/couple/CoupleViewModel.kt`
- Test: `android/app/src/androidTest/java/com/twomemory/app/NavigationTest.kt`
- Test: `android/feature/timeline/src/androidTest/java/com/twomemory/timeline/TimelineScreenTest.kt`
- Test: `android/feature/couple/src/androidTest/java/com/twomemory/couple/CoupleScreenTest.kt`

**Interfaces:**
- Consumes: Room timeline flow, profile API, theme tokens, and editor routes.
- Produces: five-tab navigation shell, continuous timeline, editable profile/theme screen, and placeholder routes for album/map until M2.

- [ ] **Step 1: Write failing navigation and accessibility tests**

Assert exact five-item order, timeline and couple active states, center action opens personal editor, bottom content is not hidden, 200% font scale keeps edit controls reachable, and system back from editor returns to the previous tab.

- [ ] **Step 2: Write failing timeline/profile tests**

Assert cursor pagination without day pages, date headers, both identity colors, shared dual-avatar marker, replaceable cover keeping old cover until upload success, editable own avatar/name only, and theme change updating local UI without mutating the partner's preference.

- [ ] **Step 3: Run and verify failure**

Run: `cd android && ./gradlew :app:connectedDebugAndroidTest :feature:timeline:connectedDebugAndroidTest :feature:couple:connectedDebugAndroidTest`

Expected: FAIL because screens/navigation are absent.

- [ ] **Step 4: Implement navigation and screens**

Match `home-timeline.png` and `couple-profile.png`; preserve fixed bottom navigation, continuous scrolling, reference hierarchy, paper detail, and identity colors. Album/map tabs use clearly labeled temporary M2 placeholders, not invented UI.

- [ ] **Step 5: Verify interactions and visual baselines**

Run: `cd android && ./gradlew :app:connectedDebugAndroidTest :feature:timeline:connectedDebugAndroidTest :feature:couple:connectedDebugAndroidTest`

Expected: PASS with warm beige and pure white screenshot artifacts.

- [ ] **Step 6: Commit**

```bash
git add android/app android/core/designsystem android/feature/timeline android/feature/couple
git commit -m "feat: add timeline navigation and couple settings"
```

### Task 10: End-to-End Two-Device Acceptance Harness

**Files:**
- Create: `server/src/test/java/com/twomemory/app/e2e/TwoDeviceSyncTest.java`
- Create: `android/core/sync/src/test/java/com/twomemory/sync/TwoDeviceScenarioTest.kt`
- Create: `scripts/verify-m1.sh`
- Create: `docs/testing/m1-acceptance.md`

**Interfaces:**
- Consumes: all Tasks 1–9.
- Produces: one repeatable M1 verification command and recorded acceptance evidence.

- [ ] **Step 1: Write failing two-device scenarios**

Model devices A and B with independent local stores. Cover: both offline create entries; reconnect in reverse order; duplicate push after simulated timeout; same-block conflict; conflict resolution; image ready but publish fails; delete versus offline edit; unauthorized third-user read.

- [ ] **Step 2: Run and verify at least one failure before harness wiring**

Run: `cd server && mvn test -Dtest=TwoDeviceSyncTest`

Expected: FAIL until fixtures and the full request path are wired.

- [ ] **Step 3: Implement the acceptance harness and verification script**

`scripts/verify-m1.sh` runs server unit/integration tests, Android JVM tests, Android instrumented tests when an emulator is available, and reports skipped device checks as incomplete rather than passed.

- [ ] **Step 4: Run complete verification**

Run: `./scripts/verify-m1.sh`

Expected: all available checks PASS; no required check is silently skipped.

- [ ] **Step 5: Perform visual comparison**

Capture 390 × 844 screenshots for home, personal editor, shared editor, and couple page. Compare them side-by-side with their files in `docs/design/reference/`; record every visible mismatch in `docs/testing/m1-acceptance.md`, fix, and repeat until no blocking mismatch remains.

- [ ] **Step 6: Commit**

```bash
git add server/src/test android/core/sync/src/test scripts docs/testing
git commit -m "test: verify stable two-device recording loop"
```

## M1 Completion Gate

M1 is complete only when:

- server and Android automated suites pass;
- two independent local stores synchronize without loss or duplication;
- same-block conflicts remain recoverable and revisioned;
- failed image publication retains both draft and ready media;
- home, both editors, and couple page pass visual comparison against approved references;
- missing Android SDK/emulator checks are resolved rather than waived;
- `docs/testing/m1-acceptance.md` contains commands, results, and known non-blocking deviations.
