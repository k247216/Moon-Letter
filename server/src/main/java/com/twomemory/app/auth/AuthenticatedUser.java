package com.twomemory.app.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

public record AuthenticatedUser(UUID userId) {

    public static AuthenticatedUser fromHeader(String rawUserId) {
        if (rawUserId == null || rawUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing authenticated user");
        }
        try {
            return new AuthenticatedUser(UUID.fromString(rawUserId));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid authenticated user");
        }
    }
}
