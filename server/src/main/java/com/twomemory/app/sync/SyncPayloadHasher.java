package com.twomemory.app.sync;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

final class SyncPayloadHasher {

    private SyncPayloadHasher() {
    }

    /** Canonical hash over everything that defines the operation's effect. */
    static String hash(SyncOperationRequest request) {
        return hash(request.operationType(), request.payload());
    }

    static String hash(String operationType, String payload) {
        String canonical = String.join("\n",
                operationType == null ? "" : operationType,
                payload == null ? "" : payload);
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
