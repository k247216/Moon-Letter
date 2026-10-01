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
import java.util.List;
import java.util.UUID;

/**
 * Turns the deployment secret into the first member, couple space and device
 * session of an installation. Repeating the call on an installation that
 * already has members reclaims the founding member's own slot, because a phone
 * that was uninstalled, lost or restored from backup still belongs to the same
 * space and the same identity. The deployment secret comes from the environment
 * and has no default production value.
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
        if (displayName == null || displayName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "display name is required");
        }
        String trimmed = displayName.trim();
        if (trimmed.codePoints().count() < 1 || trimmed.codePoints().count() > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "display name must be 1-40 characters");
        }

        Integer userCount = jdbcTemplate.queryForObject("SELECT count(*) FROM app_user", Integer.class);
        if (userCount != null && userCount > 0) {
            return reclaimFoundingMember(trimmed);
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

    /**
     * The recovery half of bootstrap. Whichever member joined first is the one
     * this installation was created for, so that is the slot the secret opens —
     * only its own sessions are revoked, and the name already on the profile
     * stays, since re-declaring a name is the rename call's job, not this one's.
     */
    private BootstrapController.BootstrapResult reclaimFoundingMember(String displayName) {
        List<FoundingMember> candidates = jdbcTemplate.query("""
                SELECT cm.user_id AS member_id, cs.id AS couple_id
                FROM couple_space cs
                JOIN couple_member cm ON cm.couple_id = cs.id
                    AND cm.left_at IS NULL AND cm.deleted_at IS NULL
                WHERE cs.deleted_at IS NULL
                ORDER BY cs.created_at, cm.joined_at, cm.user_id
                LIMIT 1
                """, (rs, rowNum) -> new FoundingMember(
                rs.getObject("member_id", UUID.class), rs.getObject("couple_id", UUID.class)));
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "this installation has no member left to recover");
        }
        FoundingMember founding = candidates.get(0);
        jdbcTemplate.update("""
                INSERT INTO user_profile (user_id, display_name, theme) VALUES (?, ?, 'WARM_BEIGE')
                ON CONFLICT (user_id) DO NOTHING
                """, founding.userId(), displayName);
        String rawToken = deviceSessionService.issueReplacementSession(founding.userId(), founding.coupleId());
        return new BootstrapController.BootstrapResult(
                founding.userId().toString(), founding.coupleId().toString(), rawToken);
    }

    static boolean secretsMatch(String configuredSecret, String suppliedSecret) {
        if (configuredSecret == null || configuredSecret.isBlank() || suppliedSecret == null) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8));
    }

    private record FoundingMember(UUID userId, UUID coupleId) {
    }
}
