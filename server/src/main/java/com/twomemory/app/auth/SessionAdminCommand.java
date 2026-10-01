package com.twomemory.app.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Local-only session recovery command (spec 2026-10-01 §5.4). When both
 * devices lose their sessions, this command — run on the server host itself
 * with database access as the only credential — revokes leftover sessions
 * and issues a replacement for a chosen member. Audit rows never contain
 * token material.
 *
 * Usage (web disabled so the command cannot run beside a live server):
 * <pre>
 * java -jar server.jar --spring.main.web-application-type=none \
 *   --spring.datasource.url=... \
 *   --moon-letter.admin.mode=revoke-all  --moon-letter.admin.user-id=UUID
 * java -jar server.jar --spring.main.web-application-type=none \
 *   --moon-letter.admin.mode=issue --moon-letter.admin.user-id=UUID \
 *   --moon-letter.admin.couple-id=UUID
 * </pre>
 * The replacement token is printed once to stdout.
 */
@Component
public class SessionAdminCommand implements ApplicationRunner {

    public enum Mode { REVOKE_ALL, ISSUE_REPLACEMENT }

    private final DeviceSessionService deviceSessionService;
    private final JdbcTemplate jdbcTemplate;

    public SessionAdminCommand(DeviceSessionService deviceSessionService, JdbcTemplate jdbcTemplate) {
        this.deviceSessionService = deviceSessionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        String modeProperty = System.getProperty("moon-letter.admin.mode");
        if (modeProperty == null || modeProperty.isBlank()) {
            return; // normal server boot
        }
        Mode mode = Mode.valueOf(modeProperty.toUpperCase().replace('-', '_'));
        UUID userId = UUID.fromString(requireProperty("moon-letter.admin.user-id"));
        String token = executeForTest(mode, userId);
        System.out.println("[session-admin] done; replacement token (shown once): " + token);
        System.exit(0);
    }

    /** Shared logic for the CLI and the recovery test. */
    public String executeForTest(Mode mode, UUID userId) {
        UUID coupleId = jdbcTemplate.queryForObject(
                "SELECT couple_id FROM couple_member WHERE user_id = ? LIMIT 1", UUID.class, userId);
        String rawToken = switch (mode) {
            case REVOKE_ALL -> {
                deviceSessionService.revokeAllForUser(userId);
                audit("REVOKE_ALL", userId);
                yield null;
            }
            case ISSUE_REPLACEMENT -> {
                String issued = deviceSessionService.issueReplacementSession(userId, coupleId);
                audit("ISSUE_REPLACEMENT", userId);
                yield issued;
            }
        };
        return rawToken;
    }

    private void audit(String action, UUID userId) {
        jdbcTemplate.update(
                "INSERT INTO session_admin_audit (id, action, target_user_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), action, userId);
    }

    private static String requireProperty(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing required property: " + key);
        }
        return value;
    }
}
