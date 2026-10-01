# SDD ledger — plan: docs/superpowers/plans/2026-10-01-media-map-ui.md

Pre-flight: existing approved guided-UI spec defines shared album and city-map contracts. This slice remains Android Compose/app-boundary work; no Room schema, server, or location SDK changes are authorized on this machine.

Task 1 ruling: album and map screens consume real `entry_blocks` projections. Empty data stays visibly empty; no demo media, map pins, counts, or fabricated stories are added.
Task 2 ruling: `AppNavigation` is the single Room-to-UI adapter. Composables receive `AlbumMediaUi`/`CityStoryUi` and only navigate back to the originating entry; they do not open Room or request location.
Task 3 ruling: focused Compose tests and screenshots remain pending on the SDK machine. Local verification is `git diff --check` and source inspection only.
