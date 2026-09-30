package com.twomemory.app.media;

import java.time.Instant;

public interface ObjectStorage {
    PresignedUpload presignPut(String objectKey, String mimeType, long byteSize, Instant expiresAt);

    ObjectMetadata head(String objectKey);

    record PresignedUpload(String url, Instant expiresAt) {
    }

    record ObjectMetadata(String sha256, long byteSize, String mimeType) {
    }
}
