# Journal note integration plan

> Execute inline using superpowers:executing-plans; prior continuous implementation authorization applies.

**Goal:** Fit notes and replies naturally into the shared journal while keeping its quiet tone.

**Architecture:** Presentation-only paper/layout components in designsystem/timeline; persist a deterministic signer ID in existing scoped profile-name preferences. Keep all record, comment, privacy and sync storage contracts unchanged.

**Spec:** docs/superpowers/specs/2026-10-26-journal-note-integration-design.md

- [x] Add failing UI tests for content-fitted note width and single-date note details/reply/send preservation.
- [x] Extract journal-note timeline/reading/reply components. Measure lines with cached Compose text layout; keep outside signatures and bounded preview.
- [x] Refine shared paper contours, keyed attachments and faint cached grain. Keep existing style/color choices.
- [x] Resolve/carry fixed signer identity through names, timeline/detail view models and comment projections; test opposite viewers see same author colour.
- [x] Render/check phone and tablet, run full Android regression, independently review and package V45 using existing signing identity. This round requires no additional server deployment beyond V44 stationery compatibility.

Review focus: font-scale wrapping, narrow screens, stable recomposition, send/draft/errors, unknown partner identity, private/rejected state controls, ordinary detail layout unchanged.

## Ledger

The new detail test first confirmed three full date occurrences, and timeline paper/signature tags were absent. After implementation, scoped profile and opposite-viewer signer tests were added. Initial full regression: 681 passed. Reviewer identified separately rounded padding versus combined width (one-pixel difference at density 3.5) and fibre-coordinate work inside draw. Both fixed; added a 560dpi text-layout test asserting two lines and no visual overflow/ellipsis. Final full regression: 682 passed, zero failure/error/skip. Final native Android14 phone 2 + landscape tablet 2 passed with a real IME. Original signing certificate verified and cover installation passed. No production mutation or database migration.
