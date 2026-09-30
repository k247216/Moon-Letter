# M1 acceptance record

## Verification command

Run from the repository root:

```bash
TEST_DB_URL=jdbc:postgresql://localhost:5432/postgres ./scripts/verify-m1.sh
```

The script deliberately reports missing PostgreSQL, Android SDK, or emulator checks as `INCOMPLETE` and exits with status `2`; skipped device checks are not a pass. On macOS, run it from a normal terminal when the test runner cannot connect to local PostgreSQL or attach the Mockito/Byte Buddy test agent.

## Scenarios

- Two independent devices create entries offline and reconnect in reverse order.
- A retry after a simulated timeout keeps one visible effect for the same operation ID.
- Same-block edits remain a conflict until a new resolving revision is written.
- Delete/edit conflicts remain recoverable rather than silently overwriting a block.
- A ready media object remains recoverable when publication fails.
- A third user cannot read another space's feed or entry.

## Current evidence

- Server migrations V1–V5 and server tests pass with the local PostgreSQL fallback.
- The full server suite currently passes 28 tests when run with the local PostgreSQL connection and JVM test-agent permissions.
- Android model JVM test passes on JDK 21 with a JVM 17 release target.
- Android SDK 37 license has not been accepted in this environment, so Room, Compose, connected-device, and screenshot checks remain `INCOMPLETE`.
- Visual comparison is pending a 390 × 844 emulator capture; the approved reference PNGs remain authoritative in `docs/design/reference/`.
