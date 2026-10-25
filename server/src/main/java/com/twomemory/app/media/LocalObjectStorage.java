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
    public PresignedUpload presignPut(String objectKey, String mimeType, long byteSize, String sha256, Instant expiresAt) {
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
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count=input.read(buffer))!=-1) digest.update(buffer,0,count);
            }
            return new ObjectMetadata(HexFormat.of().formatHex(digest.digest()), Files.size(path), null);
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

    @Override public void putVerified(String key, java.io.InputStream input, long size, String hash, String mime) throws IOException {
        putVerified(key,input,size,hash);
    }
    /** Validate completely before replacing the original; a partial upload owns only its temp file. */
    public void putVerified(String objectKey, java.io.InputStream input, long expectedSize, String expectedHash) throws IOException {
        Path path=resolve(objectKey);
        Files.createDirectories(path.getParent());
        Path temporary=Files.createTempFile(path.getParent(),".upload-",".partial");
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            long size=0;
            try(var output=Files.newOutputStream(temporary)) {
                byte[] buffer=new byte[8192]; int count;
                while((count=input.read(buffer))!=-1) {
                    size+=count;
                    if(size>expectedSize) throw new MediaValidationException("uploaded object is larger than ticket");
                    digest.update(buffer,0,count); output.write(buffer,0,count);
                }
            }
            if(size!=expectedSize || !HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedHash))
                throw new MediaValidationException("uploaded object size/checksum does not match ticket");
            Files.move(temporary,path,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch(java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        finally { Files.deleteIfExists(temporary); }
    }

    @Override public java.io.InputStream open(String objectKey) throws IOException { return Files.newInputStream(resolve(objectKey)); }

    public void verifyInput(java.io.InputStream input,long expectedSize,String expectedHash) throws IOException {
        try {
            var digest=MessageDigest.getInstance("SHA-256"); long size=0;
            byte[] buffer=new byte[8192]; int count;
            while((count=input.read(buffer))!=-1) {
                size+=count;
                if(size>expectedSize) throw new MediaValidationException("uploaded object is larger than ticket");
                digest.update(buffer,0,count);
            }
            if(size!=expectedSize || !HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedHash))
                throw new MediaValidationException("uploaded object size/checksum does not match ticket");
        } catch(java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    @Override
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
