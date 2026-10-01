package com.twomemory.app.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Disk-backed object storage for self-hosted single-server use (default
 * when moon-letter.storage.mode is not set to "s3"). Objects live under a
 * configurable base directory; integrity is verified by hashing the bytes
 * on read, which doubles as the head() metadata source.
 */
@Component
@ConditionalOnProperty(name = "moon-letter.storage.mode", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorage implements ObjectStorage {

    private final Path baseDir;

    public LocalObjectStorage(@Value("${moon-letter.storage.local-dir:./storage}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException exception) {
            throw new IllegalStateException("could not create storage directory " + this.baseDir, exception);
        }
    }

    @Override
    public PresignedUpload presignPut(String objectKey, String mimeType, long byteSize, Instant expiresAt) {
        // Local mode uploads go through the server's own /data endpoint, so
        // the presigned URL is informational only.
        return new PresignedUpload("local://" + objectKey, expiresAt);
    }

    @Override
    public ObjectMetadata head(String objectKey) {
        Path path = resolve(objectKey);
        if (!Files.exists(path)) {
            throw new MediaValidationException("object not found: " + objectKey);
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new ObjectMetadata(HexFormat.of().formatHex(digest.digest(bytes)), bytes.length, null);
        } catch (Exception exception) {
            throw new IllegalStateException("could not read stored object " + objectKey, exception);
        }
    }

    public void put(String objectKey, byte[] bytes) {
        Path path = resolve(objectKey);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("could not store object " + objectKey, exception);
        }
    }

    public byte[] read(String objectKey) {
        Path path = resolve(objectKey);
        if (!Files.exists(path)) {
            throw new MediaValidationException("object not found: " + objectKey);
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new IllegalStateException("could not read object " + objectKey, exception);
        }
    }

    public String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Guards against path traversal outside the storage base directory. */
    private Path resolve(String objectKey) {
        Path resolved = baseDir.resolve(objectKey).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new MediaValidationException("invalid object key");
        }
        return resolved;
    }
}
