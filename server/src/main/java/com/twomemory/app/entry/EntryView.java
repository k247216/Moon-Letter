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
                        List<BlockView> blocks) {

    /**
     * A personal draft is visible only to its author. The change feed is ordered
     * per couple, not per member, so the only way to keep it private is to not
     * write its rows there in the first place.
     */
    public boolean privateDraft() {
        return mode == EntryMode.PERSONAL && state == EntryState.DRAFT;
    }
}
