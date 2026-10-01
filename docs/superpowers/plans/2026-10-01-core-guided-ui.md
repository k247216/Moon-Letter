# Core Guided UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将首页、个人记录、共同记录、详情页和底部导航从当前骨架改成严格遵循指导图、由真实 Room 数据驱动且可明确反馈保存/同步状态的 Android 页面。

**Architecture:** 保留现有 Compose feature 模块和 Room 读取链路；先在 `core:designsystem` 固定视觉 token、图标和公共内容状态，再分别改 `feature:timeline`、`feature:editor` 与 `app` 接线。页面只消费 UI state，不在 Composable 内创建演示数据或直接推断远端成功。

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 primitives, Room Flow, existing `TimelineViewModel`/`EditorViewModel`, existing `SyncSession` and `AppNavigation`.

**Spec:** `docs/superpowers/specs/2026-10-01-guided-ui-and-feature-completion-design.md`

## Global Constraints

- 以六张 `docs/design/reference/*.png` 为强制视觉基线；不得使用整张指导图作为页面背景。
- 390×844 是第一轮对照尺寸；不得把它实现成固定坐标画布。
- 首页必须是左侧缝线式连续时间轴，固定底栏为 `时光 / 相册 / ＋记录 / 地图 / 我们`。
- 所有业务内容来自 Room；禁止硬编码记录、点赞、统计或演示头像作为业务数据。
- 中文正文不低于 14sp，触控目标不小于 48dp，键盘不能遮挡完成/发布入口。
- 个人和共同记录必须区分草稿、本机保存、等待同步、已同步和失败重试。
- 这台机器只改 Compose/设计系统/导航接线和验收文档；Gradle 构建、真机测试和服务端改动交给另一台机器。

## Review Focus

- 空 Room：页面保留指导图层级但显示真实空态，不能出现假记录。
- 长正文和图片：时间轴预览截断但详情完整，滚动不能吞掉返回/评论/发布。
- 离线发布：本机内容立即可读，状态显示等待同步，不能显示“对方已看到”。
- 共同记录权限：自己的视角可编辑，对方视角只读，评论不能伪装成正文。
- 主题与大字体：暖米色/纯白只换表面；200% 字体和系统状态栏不能导致按钮不可达。

### Task 1: 固定月笺视觉 token、图标和记录状态

**Files:**
- Modify: `android/core/designsystem/src/main/java/com/twomemory/designsystem/TwoMemoryTheme.kt`
- Modify: `android/core/designsystem/src/main/java/com/twomemory/designsystem/ColorTokens.kt`
- Modify: `android/core/designsystem/src/main/java/com/twomemory/designsystem/TypeTokens.kt`
- Modify: `android/core/designsystem/src/main/java/com/twomemory/designsystem/TwoMemoryIcons.kt`
- Modify: `android/core/designsystem/src/main/java/com/twomemory/designsystem/BottomNavigation.kt`
- Create: `android/core/designsystem/src/main/java/com/twomemory/designsystem/MoonLetterUiState.kt`
- Modify: `android/core/designsystem/build.gradle.kts`
- Test: `android/core/designsystem/src/test/java/com/twomemory/designsystem/DesignSystemContractTest.kt`

**Interfaces:**
- Produces `MoonLetterRecordStatus` with `DRAFT`, `LOCAL_SAVED`, `PENDING_SYNC`, `SYNCED`, `SYNC_FAILED` and user-facing Chinese labels.
- Produces `MoonLetterTabs` with exact keys/labels and semantic content descriptions.
- Produces shared colors/typography/spacing used by all later pages; no feature module defines a competing palette.

- [ ] **Step 1: Write the failing JVM design-system contract test** asserting five tab labels/order, all icon content descriptions, minimum body font size 14sp, and all record-status labels.
- [ ] **Step 2: Run the focused JVM test on the SDK machine and record the expected failures.** This Mac does not run Gradle.
- [ ] **Step 3: Implement the tokens and status model** in the listed files; use one thin-line icon family and keep warm-beige/pure-white as modes of the same structure.
- [ ] **Step 4: Re-run the focused JVM test on the SDK machine; expected result is PASS with no default Material navigation labels leaking through.**
- [ ] **Step 5: Commit** `feat(ui): establish guided visual contract`.

### Task 2: Rebuild the continuous home timeline

**Files:**
- Modify: `android/feature/timeline/src/main/java/com/twomemory/timeline/TimelineScreen.kt`
- Modify: `android/feature/timeline/src/main/java/com/twomemory/timeline/TimelineViewModel.kt`
- Modify: `android/app/src/main/java/com/twomemory/app/AppNavigation.kt` only for the route parameters if required
- Test: `android/feature/timeline/src/androidTest/java/com/twomemory/timeline/TimelineScreenTest.kt`

**Interfaces:**
- `TimelineScreen(entries: List<TimelineEntryUi>, coverBitmap: ImageBitmap?, onChangeCover: () -> Unit, onOpen: (String) -> Unit)` remains the route boundary.
- `TimelineEntryUi` must expose author, `dateLabel`, `timeLabel`, `title`, `body`, `shared`, `mine`, `photo`, `unsent`, and status text without querying Room in the Composable.

- [ ] **Step 1: Add failing Compose assertions** for cover/change-cover affordance, date grouping, author/time labels, left timeline node, media preview, full-row click, and five-tab bottom navigation.
- [ ] **Step 2: Implement the page hierarchy**: cover section, paper surface, date header, continuous left stitch, identity-colored author avatar, content preview, media card, and fixed bottom navigation spacing from the reference composition.
- [ ] **Step 3: Make the row hit target cover the entire record** while preserving separate media and comment actions; empty state must not create a fake entry.
- [ ] **Step 4: Verify the focused Compose test on the SDK machine and compare a 390×844 screenshot against `home-timeline.png`; record every structural difference.**
- [ ] **Step 5: Commit** `feat(ui): rebuild guided continuous timeline`.

### Task 3: Make record detail a complete readable destination

**Files:**
- Modify: `android/feature/timeline/src/main/java/com/twomemory/timeline/EntryDetailScreen.kt`
- Modify: `android/feature/timeline/src/main/java/com/twomemory/timeline/EntryDetailViewModel.kt`
- Modify: `android/app/src/main/java/com/twomemory/app/AppNavigation.kt` only for explicit detail callbacks
- Test: `android/feature/timeline/src/androidTest/java/com/twomemory/timeline/EntryDetailScreenTest.kt`

**Interfaces:**
- `EntryDetailScreen(state: EntryDetailUi?, onBack: () -> Unit, onDraftChange: (String) -> Unit, onSend: () -> Unit)` remains pure UI.
- `EntryDetailUi` must distinguish `unsent`, `status`, `blocks`, `comments`, `draft`, `sending`, and `error`; the screen never maps `saved=true` to remote visibility.

- [ ] **Step 1: Add failing tests** for full text visibility, all media blocks, two-author shared blocks, unsent local-read state, comment error state, and back navigation semantics.
- [ ] **Step 2: Implement the detail layout** with the same paper/identity language as the timeline, explicit status chip, complete block rendering, version/author labels for shared content, and a comment field that consumes IME/navigation insets.
- [ ] **Step 3: Ensure a missing local record shows a recoverable state** (“正在等待同步/这条记录还不在本机”) instead of a blank page.
- [ ] **Step 4: Run the focused test on the SDK machine and verify a real Room-backed record opens from a timeline row.**
- [ ] **Step 5: Commit** `feat(ui): make record details readable and stateful`.

### Task 4: Rebuild personal and shared editors with explicit save states

**Files:**
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/PersonalEditorScreen.kt`
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/SharedEditorScreen.kt`
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/EditorViewModel.kt`
- Modify: `android/app/src/main/java/com/twomemory/app/AppNavigation.kt` only for state callbacks
- Test: `android/feature/editor/src/androidTest/java/com/twomemory/editor/EditorScreenTest.kt`

**Interfaces:**
- `EditorUiState` exposes `title`, `body`, `occurrenceTime`, `timezone`, `photos`, `saving`, `saved`, `error`, and a new explicit `recordStatus: MoonLetterRecordStatus`.
- Shared editor exposes author-owned blocks; callbacks must identify the block/author rather than replacing the whole shared entry.

- [ ] **Step 1: Add failing tests** for automatic draft restoration, exact occurrence time, toolbar order, explicit local/pending/synced/failed labels, partner block read-only rendering, and IME-visible completion.
- [ ] **Step 2: Implement the personal editor** to match `personal-entry-editor.png`: natural writing surface, real author mark, time control, media toolbar, draft status and retry action.
- [ ] **Step 3: Implement the shared editor** to match `shared-entry-editor.png`: topic header, two identity-colored perspective blocks, own-block edit affordance, partner block read-only, version entry point.
- [ ] **Step 4: Update `EditorViewModel` and `AppNavigation`** so closing the editor preserves a draft, successful local persistence does not claim remote sync, and failure keeps content on screen.
- [ ] **Step 5: Run focused editor tests on the SDK machine and perform a 200% font/keyboard screenshot check.**
- [ ] **Step 6: Commit** `feat(ui): clarify personal and shared recording states`.

### Task 5: Wire the visible app shell and profile controls

**Files:**
- Modify: `android/app/src/main/java/com/twomemory/app/AppNavigation.kt`
- Modify: `android/feature/couple/src/main/java/com/twomemory/couple/CoupleScreen.kt`
- Modify: `android/core/designsystem/src/main/java/com/twomemory/designsystem/BottomNavigation.kt`
- Test: `android/app/src/androidTest/java/com/twomemory/app/NavigationTest.kt`

**Interfaces:**
- Main shell owns selected tab, editor/detail modal route, cover picker and theme callback; feature screens do not render the main bottom navigation themselves.
- Couple screen owns editable own avatar/name/theme and exposes callbacks for future anniversary/capsule/export routes without fake completed values.

- [ ] **Step 1: Add failing navigation assertions** for exact tab order, editor hides bottom navigation, timeline row opens detail, theme survives recreation, and profile changes remain local/remote-status aware.
- [ ] **Step 2: Implement shell routing** with stable back behavior, cover/theme persistence, and an in-app short-SHA/version surface for device evidence.
- [ ] **Step 3: Align the “我们” page** with `couple-profile.png`: real avatar/name controls, editable cover/theme, 中秋 semantic copy, and clearly labeled not-yet-wired routes.
- [ ] **Step 4: Run focused navigation tests on the SDK machine and capture one 390×844 shell screenshot.**
- [ ] **Step 5: Commit** `feat(ui): wire guided app shell and profile controls`.

### Task 6: Produce the first real-device handoff

**Files:**
- Modify: `docs/testing/m1-acceptance.md`
- Create: `docs/testing/real-use-log.md`
- Modify: `docs/handoff/2026-10-01-usable-app-recovery.md`

- [ ] **Step 1: Record the exact commit, APK variant, device model, Android version and server address.**
- [ ] **Step 2: On the SDK machine, install over an existing data set and complete write → open → sync → comment on two devices.**
- [ ] **Step 3: Capture screenshots for home, personal editor, shared editor and detail; compare against the four reference images and list deviations.**
- [ ] **Step 4: Record failures honestly; do not mark M1 or V1 complete from JVM tests alone.**
- [ ] **Step 5: Commit** `docs: record guided UI device handoff`.

## Execution Order

Tasks 1–5 are sequential because they share the same visual and state contracts. Task 6 starts only after Tasks 2–5 have landed. Album/map/media/time features are separate follow-up plans after this core slice has a visible, usable result; their interfaces are specified in the companion design spec and must not be faked into this slice.

## Plan Self-Review

- Spec coverage for the first usable slice: visual rules, home, personal/shared editor, detail, profile/theme, real-data and device evidence are mapped to Tasks 1–6.
- Media, album, map, music, capsule, anniversary, weekly review and export are intentionally not implemented in this first slice; they require separate data/API plans because current server/media support is incomplete.
- All later UI tasks consume the status enum and tab contract from Task 1; no task introduces a competing name or type.
- The five review-focus risks each have a focused test requirement in Tasks 2–5.
