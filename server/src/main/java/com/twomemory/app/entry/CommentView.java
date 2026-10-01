package com.twomemory.app.entry;

import java.time.Instant;
import java.util.UUID;

public record CommentView(UUID id, UUID entryId, UUID authorId, String body, UUID replyToId, Instant createdAt) {
}
