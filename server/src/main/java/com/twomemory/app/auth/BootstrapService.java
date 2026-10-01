package com.twomemory.app.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private final DeviceSessionService deviceSessionService;
    private final String configuredSecret;

    public BootstrapService(JdbcTemplate jdbcTemplate,
                            DeviceSessionService deviceSessionService,
                            @Value("${moon-letter.bootstrap.secret:}") String configuredSecret) {
        this.jdbcTemplate = jdbcTemplate;
        this.deviceSessionService = deviceSessionService;
        this.configuredSecret = configuredSecret;
    }

    @Transactional
    public BootstrapController.BootstrapResult bootstrap(String secret, String displayName) {
        if (!secretsMatch(configuredSecret, secret)) {
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
        String rawToken = deviceSessionService.issueSession(userId, coupleId);

        return new BootstrapController.BootstrapResult(userId.toString(), coupleId.toString(), rawToken);
    }

    static boolean secretsMatch(String configuredSecret, String suppliedSecret) {
        if (configuredSecret == null || configuredSecret.isBlank() || suppliedSecret == null) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8));
    }
}
