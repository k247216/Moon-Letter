package com.twomemory.app.entry;

import java.util.List;
import java.util.UUID;

public class EntryConflict extends RuntimeException {
    private final UUID entryId;
    private final List<UUID> conflictingBlockIds;

    public EntryConflict(UUID entryId, List<UUID> conflictingBlockIds) {
        super("entry changed on overlapping blocks");
        this.entryId = entryId;
        this.conflictingBlockIds = List.copyOf(conflictingBlockIds);
    }

    public UUID entryId() {
        return entryId;
    }

    public List<UUID> conflictingBlockIds() {
        return conflictingBlockIds;
    }
}
