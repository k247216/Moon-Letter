package com.twomemory.app.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Creates the first member, couple space and device session on an empty
 * installation. The deployment secret comes from the environment and has no
 * default production value.
 */
@Service
public class BootstrapService {

    private final JdbcTemplate jdbcTemplate;
    private final String configuredSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    public BootstrapService(JdbcTemplate jdbcTemplate,
                            @Value("${moon-letter.bootstrap.secret:}") String configuredSecret) {
        this.jdbcTemplate = jdbcTemplate;
        this.configuredSecret = configuredSecret;
    }

    @Transactional
    public BootstrapController.BootstrapResult bootstrap(String secret, String displayName) {
        if (configuredSecret == null || configuredSecret.isBlank() || !configuredSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid bootstrap secret");
        }
        Integer userCount = jdbcTemplate.queryForObject("SELECT count(*) FROM app_user", Integer.class);
        if (userCount == null || userCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "installation already bootstrapped");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "display name is required");
        }
        String trimmed = displayName.trim();
        if (trimmed.codePoints().count() < 1 || trimmed.codePoints().count() > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "display name must be 1-40 characters");
        }

        UUID userId = UUID.randomUUID();
        UUID coupleId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String rawToken = generateToken();
        String tokenHash = DeviceSessionAuthenticationFilter.sha256Hex(rawToken);

        jdbcTemplate.update("INSERT INTO app_user (id, status) VALUES (?, 'ACTIVE')", userId);
        jdbcTemplate.update("""
                INSERT INTO user_profile (user_id, display_name, theme) VALUES (?, ?, 'WARM_BEIGE')
                """, userId, trimmed);
        jdbcTemplate.update("""
                INSERT INTO couple_space (id, status) VALUES (?, 'UNPAIRED')
                """, coupleId);
        jdbcTemplate.update("""
                INSERT INTO couple_member (couple_id, user_id) VALUES (?, ?)
                """, coupleId, userId);
        jdbcTemplate.update("""
                INSERT INTO device_session (id, user_id, couple_id, token_hash, last_used_at)
                VALUES (?, ?, ?, ?, now())
                """, sessionId, userId, coupleId, tokenHash);

        return new BootstrapController.BootstrapResult(userId.toString(), coupleId.toString(), rawToken);
    }

    /** 256 bits of cryptographic randomness, base64url encoded. */
    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
