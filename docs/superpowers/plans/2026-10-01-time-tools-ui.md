# Time Tools and Export UI Plan

**Goal:** Continue the guided product path after timeline, detail, album and city pages by making the existing “我们” feature rows enter real, stateful UI surfaces for anniversary/countdown, time capsule, and selective export.

**Boundary:** This machine owns Compose and app navigation only. It must not invent server success, unlock capsule text locally, or claim a complete export when the SDK machine has not supplied the anniversary/capsule/export APIs. Every screen therefore exposes a clear local draft / waiting-for-sync / unavailable state and stable callbacks for the data implementation.

## Task 1 — Add explicit tool routes and callbacks

- Add an app-level route enum for anniversary, capsule and export.
- Make the existing “我们” rows semantic, 48dp-tappable and navigable; remove dead rows.
- Keep the five-tab navigation hidden while a tool route is open and make back return to “我们”.

## Task 2 — Implement guided tool surfaces

- Anniversary: 中秋语义, lunar date copy, repeat toggle, next occurrence label, editable form.
- Capsule: title/body/unlock date form, locked-before-open state, no plaintext preview before unlock.
- Export: scope selector (single record/date range/all local cache), progress/error/success states, and an explicit “等待服务端完整导出” note until the export contract is wired.

## Task 3 — Add tests and handoff

- Compose tests cover route labels, locked capsule semantics, export scope and back affordance.
- Record the data/API contracts the SDK machine must provide; do not mark M1/V4 complete from UI tests.
- This Mac only runs `git diff --check`; Gradle/device verification remains on the SDK machine.

## Task 4 — Add deterministic review surfaces

- Add “过去的今天” and “本周小结” entries to the profile page.
- Derive them from published Room entries using local calendar rules; preserve source entry IDs so every card opens the original detail.
- Show media/city tags as source evidence, never counts, scores, or AI-generated prose.
