# Memory book integrity and release acceptance

User authority: improve offline memory book design, inspect data flow/security, retain formal-product behavior. Continue without repeated design gates; privacy interpretation asked asynchronously and not expanded without an answer.

## Scope

- Keep ZIP export with original media intact. Embed browser-compatible, bounded-resolution photograph previews and the existing licensed handwriting font in index.html so photos and lettering survive opening/copying HTML alone. Audio/video originals remain streamed external files; honestly explain full extraction and codec requirements.
- Render a warm paper keepsake: editorial cover/date range, page chapters, natural-ratio framed photos, readable writing, distinct voice/movie/music notes, reply slips, responsive layout, subtle reduced-motion-aware CSS and print layout. No trackers, script execution or remote photo dependencies.
- Add a separate machine-readable record archive, manifest and original-media hashes to the ZIP; existing local text export stays explicitly a cache export, not whole-cloud backup. Do not promise recovery of albums/drafts/locked capsules omitted from this format.
- Fail rather than silently produce a complete-looking book with missing/corrupt photographs. Decode sampled images and honor orientation. Never load a whole video into memory.
- Inspect token handling, tenant/owner isolation, media reads, sync concurrency/retries, deletes, storage configuration and export leaks. Fix confirmed bounded defects with regression tests. Cloud console settings and real two-device/network behavior require separate real acceptance.

## Non-goals

No destructive production writes, data resets, re-pairing, signing changes, blanket media privacy controls, cloud all-data restore UI, or claim of full physical/cloud acceptance without evidence.

## Acceptance

Real valid image fixtures: inline preview decodes; natural proportions and original bytes preserved; malformed/missing media fails with actionable explanation; text/URL injection blocked; sensitive link fields stripped in text/JSON exports; SHA-256 manifest matches original bytes. Tests exercise actual exporter, not copied HTML. Full Android/server suites and build; browser/device results separately reported.
