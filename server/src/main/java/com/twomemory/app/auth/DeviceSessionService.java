package com.twomemory.app.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Issues opaque device-session bearer tokens. Only the SHA-256 hash is
 * persisted; the raw token is returned exactly once to the caller.
 */
@Service
public class DeviceSessionService {

    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public DeviceSessionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Creates the member's single active session and returns the raw token. */
    @Transactional
    public String issueSession(UUID userId, UUID coupleId) {
        String rawToken = generateToken();
        String tokenHash = DeviceSessionAuthenticationFilter.sha256Hex(rawToken);
        jdbcTemplate.update("""
                INSERT INTO device_session (id, user_id, couple_id, token_hash, last_used_at)
                VALUES (?, ?, ?, ?, now())
                """, UUID.randomUUID(), userId, coupleId, tokenHash);
        return rawToken;
    }

    /**
     * Revokes every still-active session of the member, then issues a fresh
     * one (device replacement / recovery rotation). Revoking first keeps the
     * single-active-session constraint satisfiable.
     */
    @Transactional
    public String issueReplacementSession(UUID userId, UUID coupleId) {
        revokeAllForUser(userId);
        return issueSession(userId, coupleId);
    }

    /** Revokes all active sessions of the member; returns the revoked count. */
    @Transactional
    public int revokeAllForUser(UUID userId) {
        return jdbcTemplate.update(
                "UPDATE device_session SET revoked_at = now() WHERE user_id = ? AND revoked_at IS NULL",
                userId);
    }

    /** 256 bits of cryptographic randomness, base64url encoded (43 chars). */
    public String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
