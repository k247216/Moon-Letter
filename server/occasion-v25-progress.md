# Backend task 1 — docs/superpowers/plans/2026-10-04-occasion-cards.md

Scope: direct checkout, server/ writes only, no Git mutations; these explicit user constraints override skill worktree/ledger/commit helper defaults.
Skills: TDD and writing-good-tests, systematic-debugging, executing-plans, verification-before-completion. Spec and plan read.

Contract: GET full live snapshot; PUT UUID returns the agreed fields; DELETE 204. Shared days are editable by either member. Surprise author is immutable and controls edits/deletion. A partner cannot convert another author's shared card to SURPRISE.

Ruling: future surprises are absent from recipient lists until the scheduled date in Asia/Shanghai; occasion media grants follow the same gate. Annual repeat does not conceal already delivered cards on other days; Android filters today's openings. Cost if misunderstood: client may need to refresh the snapshot at Shanghai midnight.
Ruling: yearless Gregorian anniversaries use 2000 to preserve month/day including Feb 29. Invalid Gregorian dates, lunar rows, and spaces without an active author are retained in the legacy table but cannot be imported as valid new cards. Cost: unsupported old records remain accessible only through the legacy API. Old endpoints are not rewritten or dual-written.
Ruling: omitted repeatsYearly defaults true for BIRTHDAY, false for ANNIVERSARY/SURPRISE; explicit values are respected. Omitted body becomes empty. Cost: clients wanting annual anniversaries must send true.
Ruling: deleted UUIDs are tombstones, excluded from GET snapshots; PUT retry gets 410 and cannot resurrect them. Cost: the client must replace a successful full snapshot instead of merging deleted items indefinitely.

RED: run-occasion-tests.ps1 -> 12 tests, 11 expected route 404 failures and 1 missing occasion table error (occasion-red.log).
GREEN: initial implementation -> 12/12 passed (occasion-green.log).
Whole suite attempt: 112 tests, 0 assertion failures, 15 startup errors in existing HTTP integration tests caused by Windows NIO loopback initialization; existing Tomcat NIO2 opt-in applied in test runner (occasion-full.log).
Review finding: deleted album_item could still grant readAsset authorization and allow a private photo to be reused in an occasion. Regression RED observed 200 instead of 403 (occasion-media-red.log). Added i.deleted_at IS NULL to that existing permission branch.
Additional checks: real original-byte download before due/on due/after withdrawal; real bearer session filters; disabled actor; migration V12->V13.
Fixture correction: bearer test initially used a nonexistent device_name column; fixed from the V6 schema. Other 14 focused tests passed in that run (occasion-green-final.log, to be superseded).

Full GREEN: run-occasion-tests.ps1 -Tests '' -Log occasion-full-final.log -> 115 tests, 0 failures/errors/skips. Existing Windows fallback resolves all 15 original HTTP context startup errors. 15 occasion tests pass, including real bearer filters and original download authorization.
Independent-name package GREEN: run-occasion-tests.ps1 -Tests '' -Goal package -JarName moon-letter-occasions-v25-20261004 -Log occasion-package-final.log -> 115 tests, 0 failures/errors/skips, BUILD SUCCESS. Both NIO2 and preferIPv4Stack settings applied. The runnable Spring Boot jar contains all occasion classes and V13 migration.
Final review: self-review (no callable subagent tool); permissions, UUID conflict race, tombstones, strict ISO calendar validation, shared asset reuse, and migration compatibility checked. No Android implementation or deployment claimed.

## Exact source/support files changed by this task

- `E:/MoonLetter/Moon-Letter/server/src/main/java/com/twomemory/app/occasion/OccasionController.java` (new)
- `E:/MoonLetter/Moon-Letter/server/src/main/java/com/twomemory/app/occasion/OccasionDtos.java` (new)
- `E:/MoonLetter/Moon-Letter/server/src/main/java/com/twomemory/app/occasion/OccasionService.java` (new)
- `E:/MoonLetter/Moon-Letter/server/src/main/resources/db/migration/V13__occasion_cards.sql` (new)
- `E:/MoonLetter/Moon-Letter/server/src/main/java/com/twomemory/app/media/MediaService.java` (existing dirty file: only occasion read permission union and deleted album-item read exclusion added)
- `E:/MoonLetter/Moon-Letter/server/src/test/java/com/twomemory/app/occasion/OccasionApiTest.java` (new: 13 PostgreSQL tests)
- `E:/MoonLetter/Moon-Letter/server/src/test/java/com/twomemory/app/occasion/OccasionAuthenticationTest.java` (new: 1 PostgreSQL + real-filter test)
- `E:/MoonLetter/Moon-Letter/server/src/test/java/com/twomemory/app/occasion/OccasionMigrationTest.java` (new: 1 real V12 upgrade test)
- `E:/MoonLetter/Moon-Letter/server/run-occasion-tests.ps1` (new: secret-safe existing-container credential lookup, dedicated test database, offline Maven)
- `E:/MoonLetter/Moon-Letter/server/occasion-v25-progress.md` (this record)

Generated files are exclusively under server/: occasion-red.log, occasion-green.log, occasion-full.log, occasion-media-red.log, occasion-green-final.log, occasion-full-final.log, occasion-package-final.log; target/ compiler output, surefire reports, named jar and .jar.original; test media storage under server/storage/ and server/target/. New isolated database moon_letter_occasion_v25_test and randomly named migration-test schemas contain test fixtures only. Existing suite also uses its established dedicated test databases. No application database or running server was changed.

## Commands and results

Working directory: `E:/MoonLetter/Moon-Letter/server`.

1. `./run-occasion-tests.ps1` (original runner default: OccasionApiTest,OccasionMigrationTest; occasion-red.log): expected RED, 12 tests, 11 failures (missing routes), 1 error (missing new table).
2. `./run-occasion-tests.ps1 -Log occasion-green.log`: first GREEN, 12/12.
3. `./run-occasion-tests.ps1 -Tests '' -Log occasion-full.log`: 112 tests, 0 failures, 15 Windows NIO startup errors; superseded by full GREEN below.
4. `./run-occasion-tests.ps1 -Tests 'OccasionApiTest#deletedAlbumPhotoCannotLaunderPrivateMediaIntoAnOccasion' -Log occasion-media-red.log`: expected RED, 1 failure (403 expected, 200 actual).
5. `./run-occasion-tests.ps1 -Tests 'OccasionApiTest,OccasionMigrationTest,OccasionAuthenticationTest' -Log occasion-green-final.log`: 15 tests, 14 passed, 1 fixture SQL error; corrected nonexistent device_name column and reran whole suite.
6. `./run-occasion-tests.ps1 -Tests '' -Log occasion-full-final.log`: 115/115 GREEN with NIO2.
7. `./run-occasion-tests.ps1 -Tests '' -Goal package -JarName moon-letter-occasions-v25-20261004 -Log occasion-package-final.log`: 115/115 GREEN, runnable package SUCCESS with NIO2 + IPv4.

Final underlying Maven command: `D:/apache-maven-3.9.15/bin/mvn.cmd -o package -Dmoon-letter.server.tomcat.nio2-connector=true -Dmoon-letter.jar-name=moon-letter-occasions-v25-20261004`, with JAVA_HOME=`E:/jdk21-extract/jdk-21.0.2`, JAVA_TOOL_OPTIONS=`-Djava.net.preferIPv4Stack=true`; TEST_DB_* passed privately from the existing container configuration.

`git diff --check -- server` and targeted MediaService whitespace check passed. `E:/jdk21-extract/jdk-21.0.2/bin/jar.exe tf target/moon-letter-occasions-v25-20261004.jar` confirmed BOOT-INF occasion classes and db/migration/V13__occasion_cards.sql. Archive size: 47,502,268 bytes. SHA-256: `9CA4DAC17E843137BBA009545F9C283D2DE287FC75D8B562D20F7D41972E0DB3`.

Parent handoff: `E:/MoonLetter/Moon-Letter/server/target/moon-letter-occasions-v25-20261004.jar` is ready for native cross-device tests. DTO matches agreed client fields exactly. Future surprise recipient list/media visibility is gated to Shanghai scheduled day. GET is a full live snapshot (no tombstone objects). New and legacy APIs are independently stored after the one-time migration. Native client tests remain the parent's task.
