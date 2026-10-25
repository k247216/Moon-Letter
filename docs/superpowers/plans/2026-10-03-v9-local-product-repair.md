# V9 local product repair implementation plan

> **For agentic workers:** Use superpowers:executing-plans for main integration and superpowers:dispatching-parallel-agents for disjoint sidecars. Steps track observable tests, not source-text assertions.

**Goal:** Apply all non-cloud v8 feedback and produce a tested v9 APK.
**Architecture:** Keep Room/outbox and private media originals. Share journal components across home/city, introduce real recording and provider music cards, restore block-oriented shared writing, distinguish publish intent from private drafts, and add verifiable local reminders.
**Tech Stack:** Kotlin/Compose, Android 26–37, MediaRecorder/MediaPlayer, WorkManager, existing sync protocol.
**Spec:** `docs/superpowers/specs/2026-10-03-product-completion-and-cloud-design.md`; user explicitly postponed ALL cloud work and requested unified execution.

## Global Constraints

- No cloud provisioning/deployment/purchases, commits or pushes; preserve current user worktree.
- No fake partner text, waveform, successful synchronization, music playback or capsule cloud security.
- Private drafts stay private; published pending content queues quietly.
- Keep independent albums independent. Default album sets, with all-photos browsing.
- Main alone runs shared Gradle/adb; workers edit disjoint files, submit tests before production work.

## Review Focus

- Permission denial, short recordings and interruption must not add unusable audio or retain microphone.
- Invalid/provider links must not execute unsafe schemes; unknown shares remain ordinary text.
- Large font/keyboard/long text must keep actions reachable and avoid excessive horizontal splits.
- Pending publish differs from genuine private draft; prior records never auto-publish from mere UI changes.
- Notification disabled/restart/time changes must not create repeated reminders or expose locked capsules.

### Task 1: Home and city journal integration (main)

Files: TimelineScreen.kt, TimelineViewModel.kt, FutureFeatureScreens.kt, AppNavigation.kt, MainActivity.kt.
Interfaces: shared `JournalEntryCard(entry: TimelineEntryUi, onOpen: (String)->Unit, ownAvatar: ImageBitmap? = null)`; keep existing routes compatible.
- [ ] RED: native tests assert cover <=27%, second record begins on first normal screen, title/group semantics, city photo/story opens same entry, window cover under status bar.
- [ ] Implement 22–25% cover with safe content insets, compact bounded preview and paper group; share city card and actual city media projection.
- [ ] GREEN: native timeline/app visual suites; pull actual rendered screenshots.

### Task 2: Audio and music sidecar

Files: EditorPhotos.kt, ShareReceiverActivity.kt, new focused recording/music helpers and tests; no PersonalEditorScreen/EditorViewModel/AppNavigation writes.
Interfaces: existing EditorAttachmentActions.pickAudio becomes record-panel launch; existing `EditorViewModel.addMusic(title, artist, url)` remains; reusable design-system audio/music components report actual availability.
- [ ] RED: safe provider text share classification, music-only metadata allowed, recording confirm/cancel/invalid file, audio playback lifecycle.
- [ ] Implement foreground permission/record/stop/preview/confirm flow; provider-share conversion preserving original text fallback; safe music opening and audio playback UI.
- [ ] GREEN: selected JVM/native real-component tests; physical audio/music-app validation remains explicitly separate.

### Task 3: Detail and block editor sidecar

Files: feature/editor except EditorPhotos.kt; EntryDetailScreen.kt, EntryDetailViewModel.kt and tests. No app/core writes.
Interfaces: expose queue-aware detail parameter with compatible default; personal/shared editor callbacks remain source-compatible, add optional callback for save draft if needed; use Task 2 components when ready.
- [ ] RED: pending publish retains response input without false permanent-local claim; shared contribution semantics/no invented partner; six toolbar slots, selected city visible, photo viewer and response preservation.
- [ ] Implement designed memory page, response bubbles, paper/block shared editor, location/draft strip, compact six tools, metadata music fallback.
- [ ] GREEN: editor/timeline native+JVM suites.

### Task 4: Local tools and independent albums sidecar

Files: feature/couple, app/notifications, IndependentAlbumScreen.kt, MoonLetterApplication.kt and new focused local reminder helpers. No AppNavigation edits.
Interfaces: add optional notification-test/system-settings callbacks to CoupleRoute; main wires them. `LocalRelationshipReminderScheduler.schedule(context)` called after local tool saves; shared prefs keys remain compatible.
- [ ] RED: anniversary/due-capsule date rules, notification test action/denial, reminder de-dup/restart; albums default sets and all-photos switching.
- [ ] Implement local configured reminders with honest delivery limits, short-test capsule route, local capsule status, album view switch; do not claim cloud lock or exact background time.
- [ ] GREEN: couple/app/native test runs coordinated by main.

### Task 5: Integration and delivery (main)

Files: app wiring, existing shared-write path if protocol supports it, build.gradle.kts, acceptance/README/handoff.
- [ ] Wire all callbacks, recording permission, music incoming shares, reminder scheduling and publish intent. Verify real same-entry shared append; no fake standalone editor success.
- [ ] Fresh read-only whole-change review; Important fixes RED→GREEN.
- [ ] Run full JVM project command and native suites, name any baseline/environment gates; verify real screenshots.
- [ ] Build unique versionCode=9 APK, install without clearing data, verify version/signature/hash; report implemented/local-only/unverified separately.

## Execution ledger

- Ruling: user approved non-cloud spec and explicitly requested continuous execution; approval/check-in/commit/worktree defaults waived in favor of in-place uncommitted work. No broad cleanup.
- Baseline: v8 native 79 passed previously; current audit rerun app visuals 4/timeline visual 1 passed. JVM Android9 runtime 7 and credential gate 3 are known baseline failures, will be reported on current full run.
- Pre-flight: main alone owns AppNavigation; sidecar parameters default compatible. Audio sidecar owns EditorPhotos; editor sidecar owns PersonalEditor/SharedEditor/EditorViewModel. Shared media helpers only in designsystem avoid app↔feature dependency cycles.
