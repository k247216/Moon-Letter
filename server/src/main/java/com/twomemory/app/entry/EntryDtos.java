package com.twomemory.app.entry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

enum EntryMode { PERSONAL, COLLABORATIVE }

enum EntryState { DRAFT, PUBLISHED, CAPSULE_LOCKED, ARCHIVED }

enum BlockType { TEXT, IMAGE, VIDEO, AUDIO, MUSIC, LOCATION }

record BlockMutation(UUID blockId, BlockType type, long orderKey, UUID authorId,
                     String payload, UUID assetId, boolean deleted) {
}

record BlockView(UUID id, BlockType type, long orderKey, UUID updatedBy,
                 long blockVersion, String payload, UUID assetId, boolean deleted) {
}

record CreateEntryCommand(UUID authorId, UUID coupleId, EntryMode mode, String title,
                          Instant occurredAt, String occurredTimezone, List<BlockMutation> blocks) {
}

record EntryView(UUID id, UUID coupleId, EntryMode mode, EntryState state, UUID authorId,
                 long rowVersion, int currentRevisionNo, List<BlockView> blocks) {
}

record PublishRequest(long baseVersion) {
}

record PublishResult(EntryView entry, int revisionNo) {
}

record ApplyChangesRequest(int baseRevision, List<BlockMutation> mutations) {
}

record ApplyChangesResult(EntryView entry, boolean merged) {
}

record ResolveConflictCommand(int baseRevision, List<BlockMutation> mutations) {
}

record CommentRequest(String body, UUID replyToId) {
}

record CommentView(UUID id, UUID entryId, UUID authorId, String body, UUID replyToId, Instant createdAt) {
}

record TimelineCursor(Instant occurredAt, UUID id) {
}

record TimelinePage(List<EntryView> entries, TimelineCursor nextCursor) {
}
