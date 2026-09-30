package com.twomemory.app.sync;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class SyncPayloadHasher {

    private SyncPayloadHasher() {
    }

    static String hash(SyncOperationRequest request) {
        String canonical = String.join("\n",
                request.entityType(),
                request.entityId().toString(),
                request.operation(),
                request.body());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte item : digest) {
                hex.append("%02x".formatted(item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
