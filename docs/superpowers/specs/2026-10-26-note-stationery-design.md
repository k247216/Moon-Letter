# Little-note stationery

The user confirmed that the visually awkward timeline strip is the new text-only little note and requested selectable styles and colours. They previously delegated routine design decisions and continuous implementation. Proceed without another approval gate.

## Design decision

Keep a restrained stationery family instead of adding stickers or a full theme editor. Three treatments: a small piece of tape (贴贴), softly ruled paper (横线), and an understated envelope seam (信笺). Five pale colours: cream, rose, sage, mist blue and lilac. Dark warm ink remains readable in every palette. Avoid the current heavy clipped corner, bottom decoration, solid dark tape and oversized dead space. Timeline cards use smaller padding and no continuous motion; composer keeps the existing one-shot opening/posting feedback.

An unobtrusive “换张纸” control reveals the choices beneath the writing surface. Real paper thumbnails preview styles; labelled colour swatches expose selection to accessibility services. Controls remain at least 48dp. Choice updates the actual writing surface immediately; the keyboard never covers posting controls.

## Data and reliability

Persist appearance on each note in the existing TEXT block JSON as a versioned optional notePaper object. Preserve text verbatim. Existing records without metadata retain a calm default; unknown future values fall back safely. No database migration, media changes, privacy changes or data rewrite. The sync serializer and backend dispatcher both reconstruct TEXT payloads, so add a narrowly allowlisted optional notePaper field at those boundaries and prepare a compatible backend update. Reuse the existing authenticated sync/outbox path, and prove appearance survives to the second device through the real local test server. Scratch recovery/idempotency must compare text, visibility and appearance together, so a late acknowledgement cannot discard a changed choice. Do not introduce per-user colour meanings.

## Acceptance

Compose, timeline and detail show the chosen paper; restart recovers unsent choices; retry creates no duplicate; old notes remain readable; second-device projection preserves choices. Phone, landscape/tablet, long text and IME paths remain usable. Static card drawings use cached paths and no list animations. Existing recording/album/media tests remain green.
