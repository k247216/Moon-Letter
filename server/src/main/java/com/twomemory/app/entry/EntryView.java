package com.twomemory.app.entry;

import java.util.List;
import java.util.UUID;

/**
 * Full entry projection served to clients and used as the change-feed
 * payload: includes title and occurrence metadata so a pulling device can
 * reconstruct the entry completely.
 */
public record EntryView(UUID id, UUID coupleId, EntryMode mode, EntryState state, UUID authorId,
                        long rowVersion, int currentRevisionNo, String title,
                        long occurredAtEpochMillis, String occurredTimezone,
                        List<BlockView> blocks, EntryVisibility visibility) {

    public EntryView(UUID id, UUID coupleId, EntryMode mode, EntryState state, UUID authorId,
                     long rowVersion, int currentRevisionNo, String title,
                     long occurredAtEpochMillis, String occurredTimezone, List<BlockView> blocks) {
        this(id, coupleId, mode, state, authorId, rowVersion, currentRevisionNo, title,
                occurredAtEpochMillis, occurredTimezone, blocks, EntryVisibility.SHARED);
    }

    /**
     * Drafts remain local to the author. Published PRIVATE records use the
     * actor-specific cloud feed, so they must not be suppressed as drafts.
     */
    public boolean privateDraft() {
        return mode == EntryMode.PERSONAL && state == EntryState.DRAFT;
    }
}
