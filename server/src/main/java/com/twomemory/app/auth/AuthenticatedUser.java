package com.twomemory.app.auth;

import java.security.Principal;
import java.util.UUID;

/**
 * Authenticated principal resolved from a device bearer session.
 * Never constructed from client-supplied headers in production.
 */
public record AuthenticatedUser(UUID userId, UUID coupleId, UUID sessionId) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }
}
