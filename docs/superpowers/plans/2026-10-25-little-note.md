# Little Note Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Tasks use checkbox tracking.

**Goal:** Turn 记下今天 into a beautiful, reliable text-only sticky note without disturbing full records.
**Architecture:** Native Compose composer and timeline note card; existing PERSONAL/TEXT transaction and cloud wire. Scoped local pending text, stable transaction id and binding checks.
**Tech Stack:** Existing Kotlin/Compose/Room/WorkManager, no new dependencies.
**Spec:** docs/superpowers/specs/2026-10-25-little-note-design.md

## Global Constraints
- Preserve all existing records, media, drafts, identity and synchronization.
- No backend schema or deployment. Only text in notes, maximum500 Unicode code points, no minimum beyond nonblank.
- Native inline execution authorized by continued user instructions; preserve dirty checkout, no commits of previous work.

## Review Focus
- Offline/failed save: text survives, no duplicate record on retry.
- Account switch: old draft cannot post into new identity.
- IME/insets, large font and tablet width: publish/back remain reachable.
- Fast duplicate taps/lifecycle disposal: no clearing a newer draft or duplicate publication.
- Older clients/export: plain TEXT remains readable; title-based presentation collision is cosmetic only.

### Task1: persistence and projection
Files: app/LittleNoteStore.kt, app/LittleNotePublishing.kt, model/EntryModels.kt, timeline/TimelineViewModel.kt; tests app/LittleNoteTest.kt.
- [ ] Write and run tests: scoped restore, blank/oversize rejection, real Room published text+outbox atomicity, idempotent retry, title presentation, foreign binding rejection.
- [ ] Implement scoped pending state, captured binding and stable entry id. Commit local transaction before network enqueue. Use existing privacy capability for PRIVATE.
- [ ] Targeted green, record outcome below.

### Task2: native composer and card
Files: app/LittleNoteComposer.kt, designsystem/StickyNotePaper.kt, timeline/TimelineScreen.kt, detail/EntryDetailScreen.kt, app/AppNavigation.kt; tests app/LittleNoteScreenTest.kt.
- [ ] Write failing UI tests for direct entry, text-only controls, empty disabled, draft restore, successful return, failure retains text and accessible phone/tablet screenshots.
- [ ] Build paper components and one-shot motion, connect only timeline create (full create unchanged). Keep failed/unsent text.
- [ ] Run focused tests, inspect rendered phone/tablet images and refine.

### Task3: verification and delivery
- [ ] Run complete Android/core regression with existing local test server; independent final code review.
- [x] Version43 APK same signer; verify manifest/hash and deliver release notes. No uninstall/server deployment.

## Execution ledger
Ruling: reuse current dirty feature checkout, as all completed but uncommitted app work lives here; new worktree at HEAD would lose it. No resets, stage, commits or unrelated changes.
Pre-flight: Task2 consumes Task1 store/publish interface; full editor draft slots remain separate.
Task1: complete — missing-feature RED compilation observed in little-note-v43-red-contract.log;8 persistence/privacy/projection tests GREEN in little-note-v43-ui-final.log. Real Room transactions and idempotent retry verified.
Task2: complete —6 composer tests and1 real AppNavigation test GREEN; phone/tablet captures inspected. Full-editor Sharesheet handoff passes via unchanged 新建记录 entry. Initial navigation assertion failure was a fixture error (placeholder text included); assertion now checks EditableText specifically without weakening draft-isolation behavior.
Ruling: full local regression database container/images were absent in this Docker engine. Restored bundled base image archive and created isolated local infra-postgres-1 bound only127.0.0.1:5432 with random test-only password. No cloud or previous data accessed.
Task3: in progress — full regression includes an added real-server note publication/rebuild/readability/idempotency segment in TwoDeviceRecordingLoopTest.
First whole regression: PASS (little-note-v43-android-full.log), including real-server cross-device note roundtrip.
Final review: one Important commit/disposal recovery issue accepted. Three new recovery tests reproduced it at runtime (11 tests,3 expected failures in little-note-v43-recovery-red.log). Added Room reconciliation before editing and posting; retired committed IDs preserve newer text, identical committed scratch paper clears, deleted IDs never reused. Final whole suite running.
Final: Ruling: reviewer deferred IME/caret/landscape to runtime — added Android14 emulator keyboard-open posting test, phone/tablet native-render screenshots and large-font landscape reachability test. Measured scrolling performance is not claimed; new timeline decoration is static and performs no per-card queries.
Task3: complete as part of the user's expanded paper-interaction round. Final669JVM/core tests and6Android simulator cases passed. Same signer verified; see releases/v43/README.md for combined package, hash and all acceptance details.
