# Journal-integrated notes

User supplied a detailed design direction and previously delegated continuous implementation. Implement the recommended quiet collage/minimal-paper approach, prioritizing timeline rhythm and shared paper language, then note reading/replies. Existing selectable styles/colours remain; no new wire enum, database migration, backend change, notification or media work.

- Short notes measure actual wrapped text at the current width/font scale, shrink to content and sit near the rail. Medium notes use natural height. Long notes preview five lines with an accessible open hint. No fixed content height in timeline.
- Signatures/time sit outside the paper. Keep paper colour independent from author colour. Resolve the pair's coral signer deterministically from their server member IDs and cache the mapping with existing names. Both devices resolve the same author to the same colour; absent identity data falls back to existing own/partner treatment.
- Shared paper renderer uses barely irregular edges for small tape fragments, subtle flat card/ruled contours otherwise, extremely faint bounded cached fibres and at most one small attachment. Decoration positions are stable per record ID; no live randomness, repeated tape on replies, list animation, or tilted body text.
- Note details show one date in navigation, original paper and signature beneath, followed by lighter reply papers in chronological order. Preserve original comment bodies, authors, times, draft state, errors, send callback and private/rejected operations. Other record detail layouts remain unchanged.
- Composer keeps existing behaviours, selectable paper appearance and IME-safe controls. No rewrite of historical records, scratch state or sync payloads.

Acceptance: one-line timeline paper clearly smaller than multiline paper; 1.5x font/tablet wrapping cannot clip; stable decorations and author mapping independent of viewing device; long text readable in detail; reply text/send enabled/error states unchanged; no duplicate large date on note detail. Render phone/tablet/mixed timeline screenshots and run relevant/full regressions before shipping V45.
