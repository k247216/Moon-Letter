# Moon Letter UI Reconstruction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rebuild the Android UI around the supplied Moon Letter guidance screens so the app reads as a cohesive couple-memory product rather than a collection of Material placeholders.

**Architecture:** Keep Room, ViewModels, SyncSession, callbacks, and server contracts intact. Move visual decisions into the shared design system, then make each feature screen consume those primitives. Navigation remains in `AppNavigation`; screen state and business callbacks remain at their current boundaries.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 primitives only where they do not override the paper visual language, existing Android modules and Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-10-02-ui-reconstruction-design.md`

## Global Constraints

- Treat `docs/design/reference/*.png` as the visual source of truth.
- Do not change Room entities, sync payloads, pairing semantics, or server endpoints.
- Preserve honest local/synced/rejected status copy.
- Keep the global create action as the primary action.
- Do not add fake functionality for video, voice, music, capsule, export, or map services.
- All production UI changes follow red-green-refactor with Compose tests first.
- Do not push or publish; the APK is the deliverable.

## Review Focus

- A 390dp-wide phone must keep the create CTA, save action, and bottom navigation reachable above system bars.
- Empty states must lead to one useful action and must not claim that an unconnected feature works.
- Long Chinese titles/body text must wrap without clipping and without collapsing the author/time hierarchy.
- Offline, unsynced, rejected, and synced states must remain visually distinguishable.
- Existing navigation callbacks must still open the same editor, detail, couple tool, album, and map routes.

---

### Task 1: Shared paper design system and navigation shell

**Files:**
- Modify: `android/core/designsystem/src/main/java/com/twomemory/designsystem/ColorTokens.kt`
- Modify: `android/core/designsystem/src/main/java/com/twomemory/designsystem/TypeTokens.kt`
- Modify: `android/core/designsystem/src/main/java/com/twomemory/designsystem/TwoMemoryTheme.kt`
- Modify: `android/core/designsystem/src/main/java/com/twomemory/designsystem/BottomNavigation.kt`
- Create: `android/core/designsystem/src/main/java/com/twomemory/designsystem/MoonLetterScaffold.kt`
- Create: `android/core/designsystem/src/main/java/com/twomemory/designsystem/EditorialComponents.kt`
- Test: `android/core/designsystem/src/test/java/com/twomemory/designsystem/DesignSystemContractTest.kt`
- Test: `android/app/src/androidTest/java/com/twomemory/app/NavigationContractTest.kt`

**Interfaces:**
- Produces `MoonLetterPaperScaffold`, `SectionHeading`, `PaperStatusChip`, `PolaroidFrame`, `AvatarPair`, and corrected `MoonLetterBottomNavigation` behavior for feature screens.
- Existing feature screens continue to consume `TwoMemoryColors`, `TwoMemoryTypography`, `TwoMemoryIcons`, and the bottom tab keys.

- [ ] Write failing contract tests for the five tab keys/icons, the corrected selected labels, and shared component semantics (`section heading`, `create record`, `paper status`).
- [ ] Run the focused design-system and navigation tests and confirm they fail for the current icon/key mismatch and missing component contracts.
- [ ] Implement palette, typography roles, paper scaffold, section heading, status chip, polaroid frame, avatar pair, and bottom navigation without changing data callbacks.
- [ ] Run focused tests, then the full design-system and app unit test slices.
- [ ] Record the result in the UI reconstruction ledger.

### Task 2: Timeline/home reconstruction

**Files:**
- Modify: `android/feature/timeline/src/main/java/com/twomemory/timeline/TimelineScreen.kt`
- Modify: `android/feature/timeline/src/androidTest/java/com/twomemory/timeline/TimelineScreenTest.kt`

**Interfaces:**
- Consumes the existing `TimelineEntryUi`, cover bitmap, avatar bitmap, delivery states, and `onOpen` callback.
- Produces a paper timeline with a single discoverable create action owned by the app shell.

- [ ] Add failing Compose tests for the empty state CTA, full-row open semantics, date chapter semantics, and unsynced/rejected copy.
- [ ] Run the focused tests and observe the missing semantics/visual structure.
- [ ] Implement the cover hero, date chapters, stitch rail, open paper rows, polaroid photos, status labels, and a clear empty state while removing redundant row-level card chrome.
- [ ] Run the timeline JVM/Compose test slices and check `390dp` width constraints in the test content.

### Task 3: Writing experience reconstruction

**Files:**
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/PersonalEditorScreen.kt`
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/SharedEditorScreen.kt`
- Modify: `android/feature/editor/src/main/java/com/twomemory/editor/EditorPhotos.kt`
- Modify: `android/feature/editor/src/androidTest/java/com/twomemory/editor/EditorScreenTest.kt`

**Interfaces:**
- Consumes existing `EditorUiState`, `EditorPhotoActions`, `MoonLetterRecordStatus`, `EntryMode`, and occurrence callbacks.
- Produces the same publish/close/mode/photo callbacks with a redesigned writing surface and truthful save states.

- [ ] Add failing tests for title/body semantics, save action states, mode switch labels, attachment descriptions, and shared-record partner-view copy.
- [ ] Run focused editor tests and confirm the expected contract gaps.
- [ ] Implement the editorial top bar, writing canvas, author block, shared-view timeline, sticky attachment row, and status treatment.
- [ ] Run editor tests and compile all feature modules.

### Task 4: Relationship surface reconstruction

**Files:**
- Modify: `android/feature/couple/src/main/java/com/twomemory/couple/CoupleScreen.kt`
- Modify: `android/feature/couple/src/main/java/com/twomemory/couple/RelationshipToolsScreen.kt`
- Modify: `android/feature/couple/src/androidTest/java/com/twomemory/couple/CoupleScreenTest.kt`
- Modify: `android/feature/couple/src/androidTest/java/com/twomemory/couple/RelationshipToolsScreenTest.kt`

**Interfaces:**
- Preserves `CoupleUiState`, name/avatar/theme/notification callbacks, pairing callback, and all existing tool route callbacks.
- Produces a relationship-first “我们” page and paper tool pages.

- [ ] Add failing tests for the relationship hero, anniversary CTA, pairing CTA, settings sections, and tool-page back action.
- [ ] Run focused couple tests and observe failures.
- [ ] Implement the dual-avatar hero, days-together/anniversary composition, grouped tools, pairing state, and consistent paper tool cards.
- [ ] Run couple tests and verify long copy wraps within the scroll surface.

### Task 5: Album and city visual surfaces

**Files:**
- Modify: `android/app/src/main/java/com/twomemory/app/FutureFeatureScreens.kt`
- Modify: `android/app/src/main/java/com/twomemory/app/MemoryReviewScreens.kt`
- Modify: `android/app/src/androidTest/java/com/twomemory/app/AppNavigationTest.kt`

**Interfaces:**
- Consumes existing `AlbumMediaUi`, `CityStoryUi`, `ReviewMemoryUi`, `onOpenEntry`, and city snapshot callbacks.
- Produces visual-first album, city, past-today, and weekly-review surfaces without inventing server behavior.

- [ ] Add failing tests for album empty state, month grouping, city save state, review empty state, and route return semantics.
- [ ] Run focused app UI tests and observe contract failures.
- [ ] Implement photo-first grids, map/story header, honest empty states, paper review cards, and consistent chapter headings.
- [ ] Run app UI tests and verify navigation from each bottom tab.

### Task 6: Integration verification and APK handoff

**Files:**
- Modify: `docs/testing/real-use-log.md`
- Modify: `docs/handoff/2026-10-02-takeover-status-and-pitfalls.md`

- [ ] Run `git diff --check` and scan for accidental hardcoded secrets.
- [ ] Run `:core:designsystem:testDebugUnitTest`, feature UI tests, `:app:testDebugUnitTest`, and `:app:assembleDebug` with the known JDK/TEMP setup.
- [ ] Verify APK badging, SHA-256, server health, and LAN base URL.
- [ ] Record the remaining real-device limitation: no connected `adb` device or AVD in this environment.
- [ ] Update the APK fingerprint and UI verification status in the handoff log.
