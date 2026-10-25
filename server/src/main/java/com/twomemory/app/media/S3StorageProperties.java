package com.twomemory.app.media;

import java.net.URI;
import java.util.Objects;

/** Runtime-only credentials and endpoint for an S3-compatible private bucket. */
public record S3StorageProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess) {

    public S3StorageProperties {
        endpoint = requiredUrl(endpoint, "endpoint");
        region = required(region, "region");
        bucket = required(bucket, "bucket");
        accessKey = required(accessKey, "access key");
        secretKey = required(secretKey, "secret key");
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name + " is required").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("S3 " + name + " is required");
        }
        return normalized;
    }

    private static String requiredUrl(String value, String name) {
        String normalized = required(value, name);
        URI uri = URI.create(normalized);
        if (uri.getScheme() == null || uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("S3 " + name + " must be an http(s) URL");
        }
        return normalized.replaceAll("/$", "");
    }
}
