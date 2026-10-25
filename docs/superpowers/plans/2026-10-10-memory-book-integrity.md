# Memory book integrity implementation plan

Spec: ../specs/2026-10-10-memory-book-integrity.md

1. Reproduce export shortcomings using valid photograph fixtures, malformed image failure, self-contained HTML decode and archive/hash expectations. Run RED before changing exporter.
2. Implement bounded photo preview conversion, inline font/photos, escaped scrapbook layout, streamed originals, separate record manifest/JSON and truthful extraction instructions. Run focused GREEN tests.
3. Harden local text/JSON payload export against file paths and credential-bearing URLs; preserve visibility metadata. RED/GREEN tests.
4. Integrate export help/status and check session boundary; run full regressions, APK build and fresh code review. Preserve V32 artifacts and signing.
5. Audit data-flow/security in parallel, record verified protections/findings and actual-device/cloud gaps. Apply bounded confirmed fixes where tests support them; do not silently mark others done.

Shared interfaces: exporter ZIP names -> pendingBookFile/AppNavigation remain compatible; record metadata -> LocalExportBuilder JSON embedded in ZIP; tests share build outputs -> all builds run serially.

Review focus: local-file origins/CSP, HEIF/EXIF decoding, large-file memory, cancellation, malformed payload/credential leakage, cache-only scope, account switch while exporting, media auth vs temporary hiding, reduced-motion/print/mobile layout.
