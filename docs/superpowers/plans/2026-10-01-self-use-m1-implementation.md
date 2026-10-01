# Self-Use M1 Reliable Recording Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Do not run tasks in parallel when they touch the same database contract. Check off each item only after its stated verification passes.

**Goal:** Deliver a genuinely usable Android-first two-person diary whose personal text records survive offline use and synchronize through a real Room → HTTP → PostgreSQL → change-feed → second Room chain, before expanding media or secondary features.

**Architecture:** Android renders only Room data. A local business write and its UUID v7 outbox operation commit together; a foreground/reconnect/manual sync engine sends typed operations and atomically applies pulled pages with their cursor. Spring Boot authenticates opaque device tokens, validates couple membership, applies each mutation and appends a per-couple ordered change in one PostgreSQL transaction. Shared entries contain author-owned, versioned perspectives rather than cross-author merging.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring Security, Maven, PostgreSQL, Flyway, Testcontainers or explicit test PostgreSQL; Kotlin, Android Gradle Plugin, Jetpack Compose, Room, WorkManager, Retrofit/OkHttp, Kotlinx Serialization.

**Authoritative spec:** `docs/superpowers/specs/2026-10-01-self-use-m1-design.md`
**Standing product principles:** `docs/human-scale-principles.md` — 范围或顺序需要取舍时按该文件第 9 节让路，不得为完成度牺牲记录成本、连续性或可带走性。

**Current truth:** M1 is `NOT VERIFIED`；台账 **70/118**（复核基线时为 62/118）。S1–S5 与 A1–A3 有已接受的开发机证据；H4 的本地恢复命令与真库测试（Task 2 第 82、83 项）、严格 FIFO 与取消传播的 outbox 重试链、Keystore 密封的会话令牌、响应式时间线和 Task 11c 的每周回看/新记录通知此后已落地。仍未完成的是需要设备的证据（E1 真机互见、E2 故障恢复、H1–H8、D/U 视觉对照）与 Task 11a/11b、12/12b、13、14 的剩余子项，另加一份实现偏差清单：`EntryMode` 的遗留命名与 `RoomSyncStore.mapState`、发布草稿时 `baseVersion` 固定 0（与 create 同批入队时是对的，出现"发布早已单独创建的旧草稿"路径时必须改）、图片无磁盘缓存、主题不跨端同步、无 Room migration 测试。历史 `TwoDeviceSyncTest`/`TwoDeviceScenarioTest` 证据仍不计为 E2E。开档（真实内容第一天）的操作顺序见 `docs/testing/open-day-runbook.md`。

---

## Execution rules

- Use test-driven development: write the named failing test, run it and record the expected failure, implement the smallest correct behavior, then rerun it.
- Do not start image work until Task 11 passes end to end.
- Do not start visual comparison until screens read real Room data.
- Every task ends with a focused commit and an evidence entry in `docs/testing/m1-acceptance.md`.
- Never commit `local.properties`, `.env`, tokens, bootstrap secrets, object-storage keys or `google-services.json`.
- A fake server may support unit tests but may not be labeled E2E.
- If a listed file already exists, modify it rather than creating a parallel implementation.
- **Measure authoring cost, do not argue about it.** 每个触及编辑器的任务都要在验收记录里写下从解锁到保存成功的实际秒数与交互数（规格 §3.5）。任何让这两项变大的改动都算回归，即使它让界面更好看。
- **No statistics features at all.** 不得实现记录条数、字数、活跃或间隔天数的统计、趋势、排名、已读列表或"对方没写"提示，包括以设置项、调试入口或本地统计的形式（规格 §3.4）。
- **Migration discipline starts when real entries start.** Task 11 之后出现真实内容，此后所有 migration 必须向后兼容或有演练过的回滚，并先在一份含真实数据的副本上跑过（规格 §6.4）。
- **Task 11 green means start using it for real, that day.** 不要等 Task 14。带已知缺陷开始在两台真机上写真实记录，是唯一正确的顺序；延后一天就少一天真实记录（规格 §9.1）。
- **本文中"两台真机"= 规格 §9 第 7 条定义的两台目标设备。** 至少一台是真实 Android 手机；配对端优先用第二台真机，确实没有时用带独立应用数据目录的具名模拟器，并在验收记录写明用的是真机还是模拟器、设备名与 Android 版本。凡涉及真实系统能力（分享面板、通知到点、键盘与触控成本、视觉对照）的证据不得用 JVM 夹具代替。

## Task 0: Decisions that must precede the data layer

**Files:**
- Modify: `docs/superpowers/specs/2026-10-01-self-use-m1-design.md`（§7.3 待决标记、§5.1 密钥失效）
- Modify: `docs/handoff/2026-09-30-cross-machine-m1-handoff.md`（记录已定结论）

Task 0 只处理**单向门**：一旦有真实内容或已发布 APK 就很难改的决定。可逆的偏好项不在这里问使用者——把它们做成"以后随时能改"（Task 12b），比要求使用者在使用前先配置更符合人类尺度原则第 7、9 节。未获得明确答复时按括号内默认值继续，**但实现方不得自行改换架构方向**。

- [x] **主数据表示（规格 §7.3）**：采用方案 A（PostgreSQL 权威 + 强制全量导出）；已体现在当前规格与 Tasks 5–7 实现中。
- [ ] **开始真实使用的日期**：写下一个具体日期，不晚于 Task 11 通过之日，并在该日期于两台真机开始写真实记录。同时写下每周可投入的时间上限；超出上限时按人类尺度原则第 9 节砍范围，而不是延长日期。
- [x] **桌面图标名称**（Android 唯一被迫提前的命名决定，见规格 §8）：使用者已确认「月笺」；App 内称呼、身份色、封面和措辞仍由 Task 12b 提供可修改入口。
- [ ] Commit: `docs(decisions): fix pre-flight product decisions`.

## Task 1: Make the server reproducibly bootable

**Files:**
- Modify: `server/pom.xml`
- Create: `server/src/main/resources/application.yml`
- Create: `server/src/main/resources/application-dev.yml`
- Modify: `server/src/test/resources/application-test.yml`
- Modify: `server/src/test/java/com/twomemory/app/CoupleDiaryApplicationTest.java`
- Modify: `README.md`

- [x] Add a context test that supplies an explicit PostgreSQL URL and asserts Flyway has applied the expected schema.
- [x] Run `cd server && mvn -Dtest=CoupleDiaryApplicationTest test`; preserve the initial failure caused by missing configuration.
- [x] Define datasource, Flyway, actuator health and S3/notification defaults without embedding production secrets. The default profile must fail with a clear missing-database error instead of silently using an unrelated database.
- [x] Start PostgreSQL using `docker compose -f infra/compose.yaml up -d postgres`, then run the context test and `mvn spring-boot:run -Dspring-boot.run.profiles=dev`.
- [x] Record the health URL, command, exit code and commit in the acceptance record.
- [x] Commit: `703aa3a build(server): add reproducible runtime configuration`.

## Task 2: Replace trusted headers with device-session authentication

**Files:**
- Create: `server/src/main/resources/db/migration/V6__device_sessions_and_bootstrap.sql`
- Create: `server/src/main/java/com/twomemory/app/auth/DeviceSessionAuthenticationFilter.java`
- Create: `server/src/main/java/com/twomemory/app/auth/SecurityConfig.java`
- Create: `server/src/main/java/com/twomemory/app/auth/BootstrapController.java`
- Create: `server/src/main/java/com/twomemory/app/auth/BootstrapService.java`
- Create: `server/src/main/java/com/twomemory/app/auth/SessionAdminCommand.java`
- Create: `server/src/test/java/com/twomemory/app/auth/BootstrapAuthenticationTest.java`
- Create: `server/src/test/java/com/twomemory/app/auth/SessionRecoveryTest.java`
- Modify: `server/src/main/java/com/twomemory/app/auth/AuthenticatedUser.java`
- Modify: `server/src/main/java/com/twomemory/app/couple/CoupleController.java`
- Modify: `server/src/main/java/com/twomemory/app/entry/EntryController.java`
- Modify: `server/src/main/java/com/twomemory/app/media/MediaController.java`
- Modify: `server/src/main/java/com/twomemory/app/sync/SyncController.java`

- [x] Write real-database tests proving bootstrap works only on an empty installation with `BOOTSTRAP_SECRET`, a second bootstrap is rejected, an invalid bearer token receives 401, and a guessed `X-User-Id` grants no access. 其中"第二次 bootstrap 被拒绝"已按 H4 需要改为**找回创始成员自己的槽位**（同 userId/coupleId、轮换令牌、旧令牌立刻 401），见 `BootstrapAuthenticationTest.bootstrapIsOneTimeAndBearerTokenIsEnforced` 第 3、4 步。
- [ ] 标记 bootstrap 密钥已消费：bootstrap 成功后同一 `BOOTSTRAP_SECRET` 永久失效并有测试证明。否则恢复演练中的"删库重建"会让任何可达该端口的人抢先把空间建走。**本项已被 2026-10-01 的使用者决定取代，保持未勾选**：卸载重装后必须仍能用同一密钥找回自己的槽位，因此密钥是长期凭据而非一次性凭据（`BootstrapService.reclaimFoundingMember`，`52b1f59`）。代价写明：`BOOTSTRAP_SECRET` 泄露＝第一位成员整槽被接管，所以服务端端口不得公网可达，密钥只留在服务端本机环境。H4 不再以本项为前置条件。
- [x] Write `SessionRecoveryTest` proving that with **no valid session existing at all**（模拟两台设备都不可用），一个仅在本机可执行的管理命令能吊销残留会话、为指定成员签发新会话，且新会话可以通过真实 HTTP 拉取到该空间的全部既有数据。这是规格 §5.4 的锁死防护，缺了它，一次换机或系统重置就等于永久失去存档。证据：`51b4a4d`，`server/src/test/java/com/twomemory/app/auth/SessionRecoveryTest.java`（真实 PostgreSQL 独立库 + `RANDOM_PORT` 真实 HTTP，断言旧令牌保持 401、新令牌能读 `/api/v1/sync/changes`、审计行不含令牌明文）。**这是 JVM + 真库证据；双端真机失联演练仍归 H4。**
- [x] Implement `SessionAdminCommand` as a local-only CLI (or an endpoint guarded by a distinct `RECOVERY_SECRET` that is never equal to `BOOTSTRAP_SECRET`); record issue/revoke/restore in an audit row containing no token material. 选择规格允许的第一种形态：`--spring.main.web-application-type=none` 下的本地 CLI（`--moon-letter.admin.mode=revoke-all|issue`），`RECOVERY_SECRET` 因此不存在，README 与交接文档中的该前置条件已删除。
- [x] Add `device_session` with token hash, user/couple foreign keys, created/last-used/revoked timestamps; never persist plaintext tokens. Enforce at most one active session per member in M1 and rotate it on device replacement.
- [x] Return an opaque token only when creating a session. Use `Authorization: Bearer`; disable form login and HTTP Basic; allow only health, bootstrap and pair endpoints explicitly.
- [x] Remove production use of `AuthenticatedUser.fromHeader` and make controllers read the authenticated principal.
- [x] Run `cd server && mvn -Dtest=BootstrapAuthenticationTest test`.
- [x] Commit: `787ce73 feat(auth): add bootstrap and device bearer sessions`.

## Task 3: Implement one-time partner pairing

**Files:**
- Create: `server/src/main/resources/db/migration/V7__secure_pairing_tokens.sql`
- Modify: `server/src/main/java/com/twomemory/app/couple/CoupleController.java`
- Modify: `server/src/main/java/com/twomemory/app/couple/CoupleService.java`
- Modify: `server/src/main/java/com/twomemory/app/couple/CoupleDtos.java`
- Modify: `server/src/test/java/com/twomemory/app/couple/CoupleApiTest.java`

- [x] Add tests for a cryptographically random token, 15-minute expiry, single use, two-member maximum and token non-disclosure after creation.
- [x] Store only the token hash. Generate at least 128 bits with `SecureRandom`; revoke on successful pairing and on replacement.
- [x] Make pairing create the second member and its first device session transactionally.
- [x] Delete or migrate the old enumerable pairing-code path; do not keep two active mechanisms.
- [x] Run `cd server && mvn -Dtest=CoupleApiTest,PairingFlowTest test`.
- [x] Commit: `34e7f3e feat(couple): secure one-time partner pairing`.

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
- [x] Pass the authenticated actor into every service mutation; remove nullable-user overloads and controller paths that discard identity.
- [x] Define the shared-entry rule directly in service code: one author owns each perspective block; the partner may append their own block but cannot update the first author's block.
- [x] Call `MediaService.requireReady` for every media reference at publication.
- [x] Run the focused entry/media tests, then `cd server && mvn test`.
- [x] Commit: `1b6c41b fix(server): enforce actor and media authorization`.

## Task 5: Make mutations typed, idempotent and transactionally observable

**Files:**
- Create: `server/src/main/java/com/twomemory/app/sync/SyncOperationDispatcher.java`
- Modify: `server/src/main/java/com/twomemory/app/sync/SyncController.java`
- Modify: `server/src/main/java/com/twomemory/app/sync/SyncDtos.java`
- Modify: `server/src/main/java/com/twomemory/app/sync/IdempotencyService.java`
- Modify: `server/src/main/java/com/twomemory/app/entry/EntryService.java`
- Modify: `server/src/test/java/com/twomemory/app/sync/SyncApiTest.java`

- [x] Write tests showing a typed `CREATE_PERSONAL_ENTRY` changes the entry tables, a duplicate `(couple_id, operation_id)` has one visible effect and returns the saved response, and the same ID with different payload is rejected.
- [x] Replace arbitrary client-supplied change payload insertion with a closed operation-type dispatcher and validated DTOs.
- [x] Wrap idempotency claim, business mutation, revision creation, change append and response storage in one `@Transactional` boundary.
- [x] Prove an injected failure before commit leaves neither business data nor change rows nor a completed idempotency response.
- [x] Run `cd server && mvn -Dtest=SyncApiTest,TypedSyncOperationTest test`.
- [x] Commit: `7f84d70 feat(sync): dispatch typed idempotent operations`.

## Task 6: Replace the unsafe global cursor with a per-couple ordered feed

**Files:**
- Create: `server/src/main/resources/db/migration/V8__per_couple_change_sequence.sql`
- Modify: `server/src/main/java/com/twomemory/app/sync/ChangeFeedService.java`
- Modify: `server/src/main/java/com/twomemory/app/sync/SyncDtos.java`
- Modify: `server/src/test/java/com/twomemory/app/db/SchemaConstraintTest.java`
- Create: `server/src/test/java/com/twomemory/app/sync/ChangeFeedOrderingTest.java`

- [x] Add a concurrency test that holds transaction A, commits B, then releases A; prove a client cannot advance past an unseen committed change.
- [x] Add `couple_sync_state` and `(couple_id, space_sequence)` uniqueness. Lock the couple state row while allocating the next sequence inside the mutation transaction.
- [x] Return pages ordered by `space_sequence` with `next_cursor` equal to the highest fully returned sequence and an explicit `has_more`.
- [x] Remove reliance on a global identity as a client cursor.
- [x] Run the ordering and schema tests repeatedly, then the full server suite.
- [x] Commit: `7fa0bd5 fix(sync): serialize per-couple change sequences`.

## Task 7: Add a real server recording-loop E2E test

**Files:**
- Create: `server/src/test/java/com/twomemory/app/e2e/SelfUseRecordingLoopE2ETest.java`
- Modify or rename: `server/src/test/java/com/twomemory/app/e2e/TwoDeviceSyncTest.java`
- Modify: `server/pom.xml`
- Modify: `docs/testing/m1-acceptance.md`

- [x] Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with real PostgreSQL via Testcontainers or an explicit isolated `TEST_DB_URL`; send real HTTP requests with two bearer tokens.
- [ ] Cover bootstrap, pairing, device A personal draft/publish, device B pull, duplicate retry, unauthorized third token, shared dual perspectives, pagination and process-level server restart where feasible.
- [x] Rename the existing `FakeServer` test to identify it as a model/unit test, or remove it if redundant. It must not appear in the E2E count.
- [ ] Run `cd server && mvn -Dtest=SelfUseRecordingLoopE2ETest test`, then `mvn test`.
- [x] Record database type, command, test count, exit code and commit.
- [x] Commit: `f410974 test(server): add real HTTP PostgreSQL recording loop`.

## Task 8: Establish a compiling Android and instrumented-test foundation

**Files:**
- Modify: `android/build.gradle.kts`
- Modify: `android/gradle/libs.versions.toml`
- Modify: every Android module `build.gradle.kts` that uses Compose or instrumentation
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/feature/timeline/src/main/java/com/twomemory/timeline/TimelineScreen.kt`
- Create: `android/gradle.properties`

- [x] Run `cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest`; keep the first compiler/configuration failures as evidence.
- [x] **测试装置选型先定，不要先做：** 以 Robolectric 在 JVM 上运行**两个真实 Room 数据库 + 真实 Retrofit 到真实 Spring Boot** 作为 `Room → Outbox → HTTP → PostgreSQL → Change Feed → 第二个 Room` 的主要证明手段；模拟器侧只保留一个最小 `connectedDebugAndroidTest` smoke 和 Task 14 的录像。跨两个独立应用数据目录的 instrumented 双端测试在单人开发中极不稳定（共享 localhost 服务、两个 app 实例、网络注入），而它要证明的链路并不需要模拟器。**这是证明成本的选择，不是降低标准**：Robolectric 侧必须用真 Room、真 HTTP、真 PostgreSQL，仍不得调用 service 或使用内存假服务。
- [x] Enable Compose where Compose code exists, configure the instrumentation runner and dependencies, fix incorrect imports, align JVM targets and configure JUnit consistently.
- [x] 静态审查已确认、需在此任务内一并修掉的缺陷（不必重新发现）：缺少 `android/gradle.properties`（`android.useAndroidX` 从未设置）；除 `app` 外 5 个含 Compose 代码的模块没有 `buildFeatures { compose = true }`；`PersonalEditorScreen.kt:12` 的 `verticalScroll` import 包名错误；`ui-test-junit4` 无版本号而 Compose BOM 只加在 `implementation` 上；9 个模块均无 `testInstrumentationRunner`；feature/app 的 androidTest 缺 junit4 与 `androidx.test:core`；`core:sync` 声明了 JUnit 5 但从未 `useJUnitPlatform()`；`app` 模块不依赖 `:core:database`/`:core:network`/`:core:sync`，因此 WorkManager 根本不在 APK 里；`AndroidManifest.xml` 无 `android:theme` 且 `res/values` 无 `styles.xml`。
- [ ] Run `./gradlew :app:assembleDebug testDebugUnitTest` and one minimal `connectedDebugAndroidTest` on a named API 37 device.
- [x] Document JDK, SDK, emulator/device model and Android version; do not commit `local.properties`.
- [x] Commit: `319b6b6 build(android): establish verified app and test baseline`.

## Task 9: Implement Room store and persistent outbox first

**Files:**
- Modify: `android/core/database/src/main/java/com/twomemory/database/AppDatabase.kt`
- Modify: `android/core/database/src/main/java/com/twomemory/database/EntryDao.kt`
- Modify: `android/core/database/src/main/java/com/twomemory/database/OutboxDao.kt`
- Modify: `android/core/database/src/main/java/com/twomemory/database/LocalEntryWriter.kt`
- Create: `android/core/database/src/main/java/com/twomemory/database/RoomSyncStore.kt`
- Modify: `android/core/database/src/androidTest/java/com/twomemory/database/LocalEntryWriterTest.kt`
- Create: `android/core/database/src/test/java/com/twomemory/database/RoomSyncStoreTest.kt`

- [x] Test that entry plus outbox commit atomically, a killed/reopened database retains pending operations, duplicate pulled changes are harmless, and applying a page plus cursor is one transaction.
- [x] Implement all `SyncStore` behavior against DAOs; remove in-memory production stores and constant cursor values.
- [x] Use two separate temporary Room database files in the accepted Robolectric JVM harness to represent two devices.
- [x] Run `cd android && ./gradlew :core:database:testDebugUnitTest`（5/5；模拟器 smoke 仍由 Task 8/14 的未勾项约束）。
- [x] Commit: `ebb4f4a feat(android): persist entries outbox and sync cursor in Room`.

## Task 10: Connect Retrofit and the synchronization engine

**Files:**
- Modify: `android/core/network/src/main/java/com/twomemory/network/CoupleDiaryApi.kt`
- Create: `android/core/network/src/main/java/com/twomemory/network/RetrofitFactory.kt`
- Modify: `android/core/sync/src/main/java/com/twomemory/sync/SyncEngine.kt`
- Modify: `android/core/sync/src/main/java/com/twomemory/sync/SyncWorker.kt`
- Modify: `android/core/sync/src/test/java/com/twomemory/sync/SyncEngineTest.kt`
- Modify: `android/app/src/main/java/com/twomemory/app/TwoMemoryApp.kt`
- Modify: `android/app/src/main/java/com/twomemory/app/AppNavigation.kt`

- [ ] Add tests for strict FIFO push, saved idempotency ID reuse after timeout, pull-until-`has_more=false`, atomic cursor updates, exponential retry and 401 stopping with a re-pair message. **复核备注：现有测试覆盖单条成功/重试，但没有证明第一条可重试失败后第二条不会发送；当前实现仍会继续循环。**
- [x] Implement bearer injection, typed serialization, timeouts and error mapping with Retrofit/OkHttp.
- [ ] Wire the real `RoomSyncStore`, API and worker in the application module. Trigger sync on app start, foreground return, manual refresh and network recovery.
- [x] Keep background WorkManager as best effort; do not claim one-minute delivery and do not make FCM a dependency.
- [x] Run `cd android && ./gradlew :core:sync:testDebugUnitTest :app:assembleDebug`.
- [x] Commit: `453d978 feat(android): wire Room Retrofit synchronization`.

## Task 11: Prove the personal-text two-device vertical slice

**Files:**
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/EditorViewModel.kt`
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/PersonalEditorScreen.kt`
- Modify: `android/feature/timeline/src/main/java/com/twomemory/timeline/TimelineViewModel.kt`
- Modify: `android/feature/timeline/src/main/java/com/twomemory/timeline/TimelineScreen.kt`
- Delete: `android/core/sync/src/test/java/com/twomemory/sync/TwoDeviceScenarioTest.kt`
- Create: `android/app/src/test/java/com/twomemory/app/TwoDeviceRecordingLoopTest.kt`

- [x] Remove hardcoded production timeline/editor data. Create and edit through Room; observe timeline from Room only.
- [x] Build the accepted Robolectric harness with two independent Room database files and a real HTTP server. Device A writes while offline, reconnects and pushes; device B pulls and exposes the exact entry from its own database.
- [x] Cover reverse reconnect, duplicate timeout retry and app-process recreation. Replace the current identical-string assertion; retain it only if renamed as a serialization unit test.
- [x] Run the real server, then `./gradlew :app:testDebugUnitTest`; preserve the two-Room/real-HTTP/real-PostgreSQL evidence. 真机 smoke 与 UI 录像仍由 Task 8/11a/14 未勾项约束。
- [x] Gate: the JVM vertical slice is green; do not begin Tasks 12–13 until Task 11a 的真机使用和 H4 会话恢复也完成。
- [x] Commit: `8491f67 feat(sync): two-device recording loop end-to-end`.

## Task 11a: Start using it for real (same day Task 11 goes green)

这不是一个开发任务，是一个**必须当天执行的动作**。档案的价值只按天累积，而这条链路已经真实可用。

**Files:**
- Create: `docs/testing/real-use-log.md`
- Modify: `docs/testing/m1-acceptance.md`

- [ ] 在两台目标真机安装该 commit 产出的 APK，用真实账号 bootstrap 与配对，双方各写至少一条真实记录并完成一次双端互见。
- [ ] 在 `docs/testing/real-use-log.md` 记下开始日期、设备型号、Android 版本与当前已知缺陷清单（明确写出"带这些缺陷开始用"）。
- [ ] 宣告迁移纪律切换：此后该库中已存在不可再生内容，所有 migration 必须向后兼容或有演练过的回滚，并先在一份真实数据副本上执行（规格 §6.4）。
- [ ] 执行一次规格 §5.4 的会话恢复演练：吊销全部会话 → 用 `SessionAdminCommand` 重新签发 → 两台真机重新拉取一致。记录命令与退出码。
- [ ] 记录观察基线：未来四周内，每台设备**未经提醒的自发打开**次数。这是唯一被承认的使用指标，不得在界面内呈现任何形式（规格 §3.4）。

## Task 11b: System share intake and measured authoring cost

接住已经发生的交换，比邀请新的创作更重要。一张本来就要发给对方的照片，应该两步之内成为一条记录。

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/com/twomemory/app/AppNavigation.kt`
- Create: `android/app/src/main/java/com/twomemory/app/ShareReceiverActivity.kt`
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/PersonalEditorScreen.kt`
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/EditorViewModel.kt`

- [ ] 注册 `SEND`/`SEND_MULTIPLE` intent 过滤器（文字与图片 MIME），分享进入后直达一条**已自动保存为草稿**的可编辑记录，不出现媒介类型选择、不出现必填字段。
- [ ] 图片分享在 Task 13 之前只落地为本地待上传引用，不得因为服务端不可达而丢失正文或本地图（规格 §3.1 第 8 项与人类尺度原则第 6 节）。
- [ ] `＋记录` 直接进入书写态；失焦自动保存；进程被杀后重进恢复正文。
- [ ] 在两台真机上各计时一次：从解锁到一条纯文字记录保存成功的实际秒数与交互数，写入验收记录（目标 ≤ 10 秒、≤ 4 次交互）。此后任何使该数字变大的改动都算回归。
- [ ] Commit: `feat(android): accept shared content as a draft entry`.

## Task 11c: Weekly re-encounter and one quiet notification

回看才是回报。这条能力的实现成本极低、情感收益极高，因此不属于 M3。

**Files:**
- Create: `android/core/sync/src/main/java/com/twomemory/sync/WeeklyReviewWorker.kt`
- Create: `android/app/src/main/java/com/twomemory/app/notifications/`
- Modify: `android/feature/timeline/src/main/java/com/twomemory/timeline/TimelineViewModel.kt`
- Modify: `android/feature/couple/src/main/java/com/twomemory/couple/CoupleScreen.kt`

> 落地时的文件划分与本表不同，按"选择规则可 JVM 单测、Android 依赖留在 app"重排：纯选择与时点计算在 `android/core/sync/src/main/java/com/twomemory/sync/WeeklyReview.kt`，`PeriodicWorkRequest` 与通知落在 `android/app/src/main/java/com/twomemory/app/notifications/`（`WeeklyReviewWorker`、`NewEntryNotice`、`MoonLetterNotifier`、`NotificationPreferences`）。功能范围未变。

- [x] 每周固定时间（默认周日 20:00，可关闭、可改时间）挑出**一条**明显更早的已发布记录（优先约一年前，逐级回退），点开直达该条记录。证据：`WeeklyReviewTest` 8 例 + `NoticeChainTest`；直达用 `MainActivity.EXTRA_OPEN_ENTRY` + `AppNavigation.initialEntryId`。验收记录 22。
- [x] 找不到符合窗口内任何记录时安静跳过：不提示"本周没有内容"，不用近期记录凑数，不生成任何内容（规格 §3.1 第 14 项）。`pick()` 在 8 天以下存档返回 null，`WeeklyReviewWorker.doWork` 对 null 不发任何通知。
- [x] 对方发布新记录后，在**本端同步完成时**发一次本地通知，只写"TA 写了一条新的"，不含正文、不含数量、不累积未读数（规格 §3.1 第 16 项）。钩子是 `SyncEngineRegistry.onCycleCompleted`，签名不带条数，任何计数都传不到界面。
- [x] 两个能力都必须能在设置里一次关闭；关闭后不得留下任何计数或历史。`newEntryNoticeEnabled=false` 时写入路径会 `remove(lastNotifiedEntryId)`；回看关闭时 `WeeklyReviewWorker.cancel` 并让 `pickReviewEntryId` 再读一次开关后静默返回。
- [x] 自查并在代码评审记录中确认：本任务未引入任何统计、趋势、已读列表或"对方没写"提示（规格 §3.4）。状态恰为两个布尔 + 一个 entryId + 星期/小时 + "问过权限一次"标记，验收记录 22 附 grep 自查（H7）。
- [x] Commit: `feat(android): add weekly re-encounter and new-entry notice`（`138aaea`，文档 `6352689`）。

> 边界：本任务的绿色是 Robolectric + JVM 证据。通知是否真到点、锁屏上的观感、点开直达是否顺畅仍归 H3/U1，一台具名真机上跑过之前不得写成"设备已验证"。

> Task 11b 与 11c 不阻塞 Task 12；但 11a 必须与 Task 11 同日完成。

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

## Task 12b: Make identity and appearance editable by the user

"由使用者决定"不是一次提前的问答，而是应用里的一个入口。没有实现这条能力时，Task 14 的"像使用者自己的"无法通过——目前没有任何其他任务产出它。

**Files:**
- Modify: `android/feature/couple/src/main/java/com/twomemory/couple/CoupleScreen.kt`
- Create: `android/feature/couple/src/main/java/com/twomemory/couple/ProfileEditScreen.kt`
- Create: `android/core/database/src/main/java/com/twomemory/database/PreferenceDao.kt`
- Modify: `android/core/database/src/main/java/com/twomemory/database/AppDatabase.kt`
- Modify: `server/src/main/java/com/twomemory/app/couple/CoupleService.java`
- Modify: `server/src/main/java/com/twomemory/app/sync/SyncOperationDispatcher.java`
- Modify: `server/src/test/java/com/twomemory/app/couple/CoupleApiTest.java`

- [ ] 名字、头像与本人身份色属于 profile，经 `UPDATE_OWN_PROFILE` 类型的同步操作走同一条链路：本地写入与 outbox 在同一 Room 事务提交，伴侣端能看到"TA 改了称呼/颜色"。只能改自己的，越权路径已在 Task 4 覆盖，此处补 negative test。
- [ ] 主题（暖米色/纯白）与首页封面属于本机偏好，存 Room 并跨重启保留，明确不覆盖对方；封面默认沿用现稿，替换为一张本地图片后不得影响任何已有记录。
- [ ] 全部入口只放在「我们」页，记录路径不得多出新步骤或新选择器（规格 §3.5）。
- [ ] 测试：改称呼与身份色后伴侣端 Room 一致；改主题与封面后重启应用仍生效且不产生任何 change row；已有记录内容逐字节不变。
- [ ] `display_name` 与 `comment.body` 的长度上限按规格 §6.5 的裁定实现，未裁定前不写死较紧的一方。
- [ ] Commit: `feat(couple): make identity and appearance user-editable in app`.

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
- [ ] **备份首先是每天自己会跑的东西，其次才是一次演练。** 实现调度（cron/systemd timer/等价机制）每日自动执行，不依赖维护者记得操作；每次成功写入一行可查询的 `backup_health`（时间、规模、校验摘要）；连续失败必须在维护者下次进入服务端时明确可见，而不是只落在日志里。自托管存档的真实风险不是"没备份过"，而是"脚本某天静默失败，三个月后才发现"。
- [ ] 启用对象存储版本控制或等效防误删能力；至少存放一份**不在这台服务器上**的副本，且不与服务器共享同一把钥匙；备份介质本身加密并具备独立访问控制——泄露面不能只是从服务器搬到备份目录。
- [ ] 收集连续至少 7 天的 `backup_health` 证据行，再做一次完整恢复演练（含 Task 11a 的会话恢复），演练记录写入 `docs/testing/m1-backup-restore.md` 并注明此后每季度重复。
- [ ] Export a selected record and a date range as JSON, Markdown/HTML and original media without secrets.
- [ ] Run media/export tests and the documented restore drill.
- [ ] Commit: `feat(data): add image integrity backup and export`.

## Task 14: Complete real-data UI and final acceptance

**Files:**
- Modify: `android/feature/timeline/`, `android/feature/editor/`, `android/feature/couple/`, `android/core/designsystem/`
- Modify: `android/app/src/main/java/com/twomemory/app/AppNavigation.kt`
- Modify: `docs/testing/m1-acceptance.md`
- Modify: `README.md`

- [ ] Verify Task 12b landed (profile name/avatar, identity color, per-device beige/white theme, cover). If it has not, do not re-implement it here as a static style — raise it as a blocked dependency. Keep album and map as honest unavailable states.
- [ ] Capture 390 × 844 reference-state screenshots for timeline, personal editor, shared editor and couple profile; also test a shorter viewport, keyboard open and enlarged font.
- [ ] Compare against `docs/design/reference/` for structure, spacing, color, icon stroke, typography, scroll and navigation. Do not use screenshots as UI backgrounds.
- [ ] **验收现场是真实设备（规格 §9 第 7 条），不是参考稿的并排截图。** 参考稿与骨架代码产出自同一天，它给的是方向（手帐质感、连续缝线时间轴、双身份色层级），不是像素基线。最终判断标准三条：像纸、像使用者自己的、不像软件。
- [ ] 开箱默认值必须自己就过得去——使用者不配置也能直接用；同时现场演示 Task 12b 的入口：使用者在两台目标设备上各自改掉称呼或身份色/主题/封面，**两分钟内完成、不需要重新构建、不丢任何已有记录**。桌面图标名是唯一需要提前问的一项（两个候选二选一）。不得提供成品征求意见，也不得以"和参考图一致"为由覆盖使用者的选择（人类尺度原则第 7 节）。
- [ ] Run the full server suite, Android unit tests, connected tests, assembly, two-device scenario and backup/restore drill from a clean checkout.
- [ ] Record a complete screen capture of offline A → online server → B Room/UI on real hardware: at least one real Android phone, the paired side a second real phone or a named emulator with its own data directory (state which, plus device name and OS version). The Room-to-Room data trace itself comes from the JVM harness; this recording proves the app people actually touch.
- [ ] Update acceptance rows with exact commands, environment, commit, exit code and artifact paths. Any missing mandatory row leaves status `NOT VERIFIED`.
- [ ] Commit: `test(m1): record verified self-use acceptance evidence`.

---

## Completion boundary

Do not claim M1 complete because code compiles, 28 tests pass, a fake two-device model passes, or screenshots resemble the references. Completion requires the Task 14 evidence set on one exact commit. Video, audio, music, map stories, album organization, countdowns, capsules and “过去的今天” remain subsequent milestones; **每周回看不再属于其中，它已经在 Task 11c**。

另外两条同等有效的判定：

- **把开始使用的日期一再推后，本身就是失败**，哪怕每个 Gate 都是绿的。这份档案的价值只按天累积（Task 11a）。
- 任何使记录成本变大、或引入统计与关系记分牌的改动，即使让测试更绿，也不算完成，只能算回归（规格 §3.4、§3.5）。
