# SDD ledger — plan: docs/superpowers/plans/2026-10-01-time-tools-ui.md

Pre-flight: the approved product spec defines anniversary, capsule, past-today, weekly summary and export. This slice starts with the three existing “我们” entry points whose data contracts are already named; it does not add Room/server entities on this machine.

Task 1 ruling: tool screens are app-level routes, so the main five-tab bar stays hidden while a tool is open and Back returns to the profile page. Rows must be semantic and tappable, not decorative.
Task 2 ruling: capsule text is never displayed before its unlock date; export UI separates a local-cache draft from a server-complete export; no callback is allowed to imply remote success until the SDK machine wires it.
Task 3 ruling: focused Compose tests and screenshots remain pending on the SDK machine. Local verification is `git diff --check` and source inspection only.

Implementation note: the local-cache export action is real text sharing from published Room entries; date-range/all scopes remain explicit waiting states until the server export task supplies versioned JSON/Markdown/media ZIP endpoints. Anniversary metadata is cached locally with an honest waiting-sync label; capsule plaintext is intentionally not written to ordinary preferences.
