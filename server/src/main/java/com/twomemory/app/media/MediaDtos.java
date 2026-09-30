package com.twomemory.app.media;

import java.time.Instant;
import java.util.UUID;

enum MediaKind { IMAGE, VIDEO, AUDIO }

enum MediaStatus { LOCAL_PENDING, UPLOADING, PROCESSING, READY, FAILED, DELETED }

record CreateMediaCommand(UUID ownerId, UUID coupleId, MediaKind kind, String mimeType,
                          long byteSize, Integer width, Integer height, Long durationMs,
                          String sha256, UUID operationId) {
    CreateMediaCommand withMimeType(String value) {
        return new CreateMediaCommand(ownerId, coupleId, kind, value, byteSize, width, height, durationMs, sha256, operationId);
    }

    CreateMediaCommand withByteSize(long value) {
        return new CreateMediaCommand(ownerId, coupleId, kind, mimeType, value, width, height, durationMs, sha256, operationId);
    }

    CreateMediaCommand withWidth(Integer value) {
        return new CreateMediaCommand(ownerId, coupleId, kind, mimeType, byteSize, value, height, durationMs, sha256, operationId);
    }
}

record UploadTicket(UUID assetId, String objectKey, String uploadUrl, Instant expiresAt,
                    MediaAssetView asset) {
}

record MediaAssetView(UUID id, UUID coupleId, UUID ownerId, MediaKind kind,
                      MediaStatus status, String objectKey, String mimeType,
                      long byteSize, Integer width, Integer height, Long durationMs,
                      String sha256, String failureCode) {
}

record CompleteUploadRequest(String sha256) {
}
