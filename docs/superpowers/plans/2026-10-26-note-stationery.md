# Note stationery implementation plan

> Execute inline with superpowers:executing-plans; user has delegated continued work.

**Goal:** Make little notes fit the journal and offer a small selection of paper styles and colours.

**Architecture:** Optional typed appearance in the existing TEXT payload, projected into TimelineItem and TimelineEntryUi. Shared static Compose stationery component renders composer/cards/detail. Existing sync and database contracts stay intact.

**Tech Stack:** Kotlin, Compose, Room, existing JSON text-block protocol.

**Spec:** docs/superpowers/specs/2026-10-26-note-stationery-design.md

## Constraints / review focus

- Preserve privacy, UUID retries, scratch ownership and existing user data.
- Unknown/missing appearance is safe, readable and non-author-specific.
- Acknowledgement after a newer appearance change must retain the newer scratch.
- Partner projection and detail use identical metadata.
- Large fonts/IME/landscape do not hide publishing or force editing through a picker.

## Tasks

- [x] Add regression cases for scratch/appearance, recovery, backward compatibility and second-device round trip. Observe the new behavioural assertions fail before implementation.
- [x] Add model and optional TEXT metadata codec; persist pending appearance and compare full acknowledgements. Wire timeline/detail projections.
- [x] Refine StickyNotePaper and add an inline stationery selector with live preview, labelled 48dp options and selected semantics.
- [x] Verify focused UI/storage and real-server tests; inspect rendered phone/tablet/timeline screenshots. Run full Android regression; produce V44 APK with the existing signing identity and publish only safe source/metadata.

## Ledger

Initial decision: use optional TEXT JSON fields rather than a new table or wire enum. Inspection and failing round-trip tests established that both outbox and backend reconstruct TEXT payloads. Ruling: add a strictly allowlisted optional field at both boundaries and provide a backend update; no schema change. Old clients still read text normally. Backend regression failed on missing LETTER metadata before the fix; the Android real-server round trip failed against the prior backend, as expected.

Verification: 673 Android/core full regression cases; 209 backend cases; 9 focused UI cases after the rendering fix; 2 phone + 2 landscape-tablet native instrumentation cases, all passing. Screenshot inspection covered palette/style chooser and mixed-style timeline. Reviewer found no P1/P2 issue; the ruled thumbnail finding was fixed and re-rendered. The sandbox debug key differed from the installed test identity: fixed signing path through ignored local properties, rebuilt and verified certificate and successful cover installation. Production untouched; backend update package is prepared, not deployed.
