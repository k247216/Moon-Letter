# Journal paper language

Keep the shared book quiet: roughly 70% simple layout, 20% paper character, 10% playful accents. A note belongs to the timeline, not to a separate sticker application.

## Paper and spacing

- Reuse the five NotePaperColor palettes and the bundled Wenkai body font. No new saturated backgrounds or competing handwriting fonts.
- Timeline note body: 17sp / 27sp, 15dp horizontal and 12dp vertical paper padding. A one/two-line paper fits its measured content; no fixed height. Short notes begin 34dp from the left edge beside the existing curved rail. Read longer notes through a five-line preview.
- Reading paper: natural text height with 22dp horizontal / 24dp vertical padding; author and time outside. Do not repeat the large date header or add a second title to system little notes.
- Replies are plain, pale fragments, with outside signatures and modest stagger. Keep chronology and accessible input controls; avoid speech-bubble tails, repeated tape or chat-avatar scaffolding.

## Texture and attachment budget

- Tiny paper-edge variation, no crumples, stains, large folded corners or cast shadows.
- At most one attachment per main paper. Tape left/right, a tiny pin or no attachment are determined from the stable record ID. Never depend on list index, current viewing member or fresh randomness. Body text stays upright.
- Replies have no attachment. Fine fibres use alpha .035 and cached path geometry, bounded to 24 strokes. No texture fetches, bitmap tiling or per-frame geometry generation.
- Scrolling paper remains static. Composer opening/posting feedback can continue to use the existing short one-shot animation.

## Ownership and interaction

Paper colour is the author's choice. Coral/sage signature colour belongs to the member; resolve the coral member from the pair's stable IDs and reuse the same mapping in timeline, details and replies. Cache it by service/storage origin and space. Never infer ownership from paper colour.

Keep the entire note row tappable. Author/time/private/unread marks remain readable outside the paper; a long preview explicitly invites opening. Preserve the existing rejected-state retry, private visibility actions, comment errors, sending guard and keyboard-safe bottom controls.

## Pixel correctness

Calculate text width with the same individually rounded padding edges used by Compose. Account for current density and font scale. Preserve full original text in detail and storage; preview truncation never rewrites the record.
