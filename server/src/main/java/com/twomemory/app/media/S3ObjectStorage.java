package com.twomemory.app.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class S3ObjectStorage implements ObjectStorage {

    private final String endpoint;

    public S3ObjectStorage(@Value("${app.storage.endpoint:http://localhost:9000}") String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public PresignedUpload presignPut(String objectKey, String mimeType, long byteSize, Instant expiresAt) {
        String encoded = URLEncoder.encode(objectKey, StandardCharsets.UTF_8);
        return new PresignedUpload(endpoint.replaceAll("/$", "") + "/moon-letter/" + encoded, expiresAt);
    }

    @Override
    public ObjectMetadata head(String objectKey) {
        throw new MediaValidationException("object metadata provider is not configured");
    }
}
