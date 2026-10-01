package com.twomemory.app.entry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

enum EntryState { DRAFT, PUBLISHED, CAPSULE_LOCKED, ARCHIVED }

record BlockView(UUID id, BlockType type, long orderKey, UUID updatedBy,
                 long blockVersion, String payload, UUID assetId, boolean deleted) {
}

record PublishRequest(long baseVersion) {
}

record ApplyChangesRequest(int baseRevision, List<BlockMutation> mutations) {
}

record ResolveConflictCommand(int baseRevision, List<BlockMutation> mutations) {
}

record CommentRequest(String body, UUID replyToId) {
}

record TimelineCursor(Instant occurredAt, UUID id) {
}

record TimelinePage(List<EntryView> entries, TimelineCursor nextCursor) {
}
