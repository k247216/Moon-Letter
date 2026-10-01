# SDD ledger — plan: docs/superpowers/plans/2026-10-01-core-guided-ui.md

Pre-flight: Task 1 produces `MoonLetterRecordStatus` and the tab/icon/design-token contracts consumed by Tasks 2–5; current code has no equivalent status enum, so Tasks 2–5 must wait for Task 1. `TimelineEntryUi` and `EditorUiState` already exist and remain the feature boundaries.

Ruling: implementation stays in the current checkout because the user previously required continuing in this folder; no separate worktree is created.

Task 1: Ruling: use `ColorTokens.kt` and `TypeTokens.kt` because those are the repository's actual design-token files; use a JVM contract test because the design-system module has no existing Compose instrumentation test dependency and this Mac must not run Gradle.

Task 1: Ruling: the planned focused JVM test is written but not run on this machine; the user's explicit SDK/build boundary takes precedence. `git diff --check` is the local verification, and the SDK machine must run `:core:designsystem:test` before treating the task as complete.
