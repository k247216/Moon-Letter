package com.twomemory.app.entry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Local-first: the client generates the entry id; the server adopts it when
 * present and generates one otherwise.
 */
public record CreateEntryCommand(UUID authorId, UUID coupleId, EntryMode mode, String title,
                                 Instant occurredAt, String occurredTimezone,
                                 List<BlockMutation> blocks, UUID entryId) {
}
