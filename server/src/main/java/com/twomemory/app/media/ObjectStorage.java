package com.twomemory.app.media;

import java.time.Instant;

public interface ObjectStorage {
    PresignedUpload presignPut(String objectKey, String mimeType, long byteSize, String sha256, Instant expiresAt);

    ObjectMetadata head(String objectKey);

    byte[] read(String objectKey);
    default void putVerified(String key, java.io.InputStream input, long size, String hash, String mime) throws java.io.IOException {
        throw new UnsupportedOperationException("Verified streaming write not configured");
    }
    default void verifyInput(java.io.InputStream input, long expectedSize, String expectedHash) throws java.io.IOException {
        try {
            var digest=java.security.MessageDigest.getInstance("SHA-256"); long size=0;
            byte[] buffer=new byte[8192]; int count;
            while((count=input.read(buffer))!=-1) {
                size+=count;
                if(size>expectedSize) throw new MediaValidationException("uploaded object is larger than ticket");
                digest.update(buffer,0,count);
            }
            if(size!=expectedSize || !java.util.HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedHash))
                throw new MediaValidationException("uploaded object size/checksum does not match ticket");
        } catch(java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    /** Callers close this stream; production stores override to avoid whole-object buffers. */
    default java.io.InputStream open(String objectKey) throws java.io.IOException {
        return new java.io.ByteArrayInputStream(read(objectKey));
    }

    record PresignedUpload(String url, Instant expiresAt) {
    }

    record ObjectMetadata(String sha256, long byteSize, String mimeType) {
    }
}
