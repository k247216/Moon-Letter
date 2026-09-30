package com.twomemory.app.sync;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record MutationResult(int status, String body, boolean replayed) {
    MutationResult asReplayed() {
        return new MutationResult(status, body, true);
    }
}

record SyncOperationRequest(UUID operationId, UUID coupleId, String payloadHash,
                            String operationType, String payload) {
}

record ChangeView(long sequence, String entityType, UUID entityId, String operation,
                  String payload, Instant createdAt) {
}

record ChangePage(List<ChangeView> changes, long nextSequence, boolean hasMore) {
}
