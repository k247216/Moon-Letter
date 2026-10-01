# SDD ledger — plan: docs/superpowers/plans/2026-10-01-core-guided-ui.md

Pre-flight: Task 1 produces `MoonLetterRecordStatus` and the tab/icon/design-token contracts consumed by Tasks 2–5; current code has no equivalent status enum, so Tasks 2–5 must wait for Task 1. `TimelineEntryUi` and `EditorUiState` already exist and remain the feature boundaries.

Ruling: implementation stays in the current checkout because the user previously required continuing in this folder; no separate worktree is created.

Task 1: Ruling: use `ColorTokens.kt` and `TypeTokens.kt` because those are the repository's actual design-token files; use a JVM contract test because the design-system module has no existing Compose instrumentation test dependency and this Mac must not run Gradle.

Task 1: Ruling: the planned focused JVM test is written but not run on this machine; the user's explicit SDK/build boundary takes precedence. `git diff --check` is the local verification, and the SDK machine must run `:core:designsystem:test` before treating the task as complete.

Task 2: Ruling: keep the existing `TimelineScreen` route signature and Room-backed `TimelineEntryUi`; improve the visible composition in place so the second machine can build the same APK without a navigation/data migration.
Task 2: Ruling: the test update changes the shared marker assertion from one combined string to separate author + `共同` labels, matching the guided double-identity layout and avoiding a chat-bubble interpretation.
Task 2: Ruling: the focused Compose test and screenshot comparison are not run on this Mac; the SDK machine owns both. Local verification is limited to `git diff --check` and source inspection.

Task 3: Ruling: keep block author metadata optional at the UI boundary because the current shared `EntryBlock` model does not yet carry an author ID; the detail screen renders author labels when supplied and the service/database task must provide them before shared-version acceptance.
Task 3: Ruling: unsupported VIDEO/AUDIO/MUSIC/LOCATION blocks remain readable as explicit type messages rather than disappearing; their real renderers belong to the media/map plans.
Task 3: Ruling: focused detail Compose tests and the Room-backed device open path remain pending on the SDK machine; local verification is `git diff --check` only.
