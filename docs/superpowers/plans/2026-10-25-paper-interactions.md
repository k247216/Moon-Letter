# Paper Interactions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans; continue the authorized inline execution.

**Goal:** Finish the approved interaction round after the text-only note, then test and ship one combined APK.
**Architecture:** Shared native Compose paper buttons/date wheels; migrate existing callers without changing data contracts, reminder rules or timezones. Existing cards receive static paper boundaries, not snapping scroll behavior.
**Tech Stack:** Kotlin/Compose LazyColumn snapping and existing Room/cloud stack, no new dependencies.
**Spec:** docs/superpowers/specs/2026-10-25-paper-interactions-design.md

## Global Constraints
- Preserve previous data, draft state machines, privacy and media caching.
- No server deployment, no photo tags or forced timeline snapping.
- Explicit confirmation before date/time changes; cancel does not write.
- One final version after full tests and artifact verification.

## Review Focus
- Month-end/leap-year clamping must produce a real selected date.
- Wheels settle to the displayed value; a second wheel must not undo selection.
- Disabled actions retain the original callback/semantics contract.
- Very large fonts and short landscape screens retain confirmation access.
- Optional album category uses existing default classification, never new photo tags.

### Task1: shared components and caller migration
- [x] Add failing PaperControlsTest before components; observed missing-component RED.
- [x] Implement PaperTextButton and PaperDateTimePicker, migrate editor/anniversary/capsule/cards/review/legacy tools.
- [x] Run actual wheel selection, confirmation, cancel and disabled-action tests.

### Task2: timeline/album refinement
- [x] Static paper surface and stitched curve backing, no active scrolling animations.
- [x] Album category optional, defaults to existing 未分类 repository path; clarify album-level meaning.
- [x] Verify optional category creation and render phone/tablet components.

### Task3: completion gate
- [x] Independent review of new round; resolve important issues with tests.
- [x] Full client/model regression plus local real-backend roundtrip.
- [x] Android14 keyboard/wheel device checks when approval execution is available; do not claim unrun checks.
- [x] Same-signature version43 APK and SHA256, combined release notes and concise handoff.

## Ledger
User extended scope after the note's658-test full regression passed. Intermediate note APK was NOT delivered; final version43 will include the whole round.
Privileged command review hit account usage limit; no denied ADB command was rerun under lower permissions. Unaffected workspace code edits and ordinary sandbox builds continue. Kotlin daemon attempted an AppData write; normal compiler is now in-process to keep build state inside the authorized workspace.
Initial focused build found a Material3 API positional mismatch; fixed using named TextButton arguments. PowerShell split an unquoted -P option; subsequent invocations quote the property.
Workspace public Robolectric SDK cache found at .gradle-tmp/robolectric-runtime; using it offline avoids private Maven cache/network access. No private cache files copied. Privileged execution subsequently approved; real local Docker regression resumed normally.
Final review: three Important findings accepted. Moving-wheel confirmation and missing rotation restoration reproduced at runtime; per-wheel busy tracking gates confirm/mode switches, callbacks read live date fields and clamp, all picker visibility is saveable. Concurrent-field test passes; do not claim it failed before the defensive change.
Ruling: animation-clock-only busy test was unreliable for a separate dialog window; replaced it with a held real touch drag, followed by release/settling. Same confirmation/mode-switch assertions retained.
Ruling: optional-album creation UI repeatedly hit Robolectric dialog idle failure even without IME injection. Migrated the unchanged behavioral assertions to Android instrumentation, retaining old failed logs. Native phone run passed all3tests including two albums reusing one 未分类 group and visible create button.
Component gate:29/29 pass (paper-round-v43-components.log). Full local client/core/model gate:669/669, failure/error/skip0 (paper-round-v43-full-release.log); client and device-test APKs built. Manifestversion43 and old signer SHA25674001d60036e79f897f761523c7cdbd87580b0a04256125973ee4bf66fd8a015 verified.
Phone Android14:3/3 pass, keyboard really visible before posting. Landscape tablet gate running; test asserts actual configuration width>=600dp and width>height.
Final: complete — phone3/3 and landscape tablet3/3 passed. All669JVM/core tests passed with0failed/errors/skips. APK source/copy hash both72fc3d1a4bea3430792616ac2024af7811ecebb0fac973827d0869948f31914c; manifest version43/paper-interactions-v43-20261025, same V42 signer. No unaddressed Important/Critical finding in reviewed scope. Release README records the rulings and actual verification limits.
