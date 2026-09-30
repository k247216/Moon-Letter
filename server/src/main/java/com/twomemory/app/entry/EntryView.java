package com.twomemory.app.entry;

import java.util.List;
import java.util.UUID;

public record EntryView(UUID id, UUID coupleId, EntryMode mode, EntryState state, UUID authorId,
                        long rowVersion, int currentRevisionNo, List<BlockView> blocks) {
}
