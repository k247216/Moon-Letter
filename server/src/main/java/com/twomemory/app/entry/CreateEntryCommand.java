package com.twomemory.app.entry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateEntryCommand(UUID authorId, UUID coupleId, EntryMode mode, String title,
                                 Instant occurredAt, String occurredTimezone,
                                 List<BlockMutation> blocks) {
}
