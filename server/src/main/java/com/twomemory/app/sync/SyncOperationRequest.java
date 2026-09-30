package com.twomemory.app.sync;

import java.util.UUID;

public record SyncOperationRequest(UUID operationId, UUID coupleId, String payloadHash,
                                   String operationType, String payload) {
}
