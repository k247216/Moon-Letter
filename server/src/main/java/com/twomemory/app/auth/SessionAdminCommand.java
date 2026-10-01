package com.twomemory.app.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Local-only session recovery command (spec 2026-10-01 §5.4). When both
 * devices lose their sessions, this command — run on the server host itself
 * with database access as the only credential — revokes leftover sessions
 * and issues a replacement for a chosen member. Audit rows never contain
 * token material.
 *
 * <p>Web must stay disabled so the command cannot run beside a live server;
 * the flags are read from program arguments and, as a fallback, from system
 * properties:
 * <pre>
 * java -jar server.jar --spring.main.web-application-type=none \
 *   --moon-letter.admin.mode=revoke-all --moon-letter.admin.user-id=UUID
 * java -jar server.jar --spring.main.web-application-type=none \
 *   --moon-letter.admin.mode=issue --moon-letter.admin.user-id=UUID
 * </pre>
 * The replacement token is printed once to stdout. The couple space is never
 * supplied: it is looked up from the member's own row, so a wrong user id
 * cannot silently issue a session into someone else's space.
 */
@Component
public class SessionAdminCommand implements ApplicationRunner {

    public enum Mode { REVOKE_ALL, ISSUE_REPLACEMENT }

    static final String MODE_KEY = "moon-letter.admin.mode";
    static final String USER_ID_KEY = "moon-letter.admin.user-id";

    record Request(Mode mode, UUID userId) {
    }

    private final DeviceSessionService deviceSessionService;
    private final JdbcTemplate jdbcTemplate;

    public SessionAdminCommand(DeviceSessionService deviceSessionService, JdbcTemplate jdbcTemplate) {
        this.deviceSessionService = deviceSessionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Request request = resolveRequest(args);
        if (request == null) {
            return; // normal server boot
        }
        String token = executeForTest(request.mode(), request.userId());
        System.out.println(token == null
                ? "[session-admin] revoked every session of " + request.userId()
                : "[session-admin] done; replacement token (shown once): " + token);
        System.exit(0);
    }

    /**
     * {@code null} means "this is an ordinary server start". Program arguments
     * win over system properties so the invocation in README.md works as written.
     */
    Request resolveRequest(ApplicationArguments args) {
        String modeProperty = firstValue(args.getOptionValues(MODE_KEY), System.getProperty(MODE_KEY));
        if (modeProperty == null) {
            return null;
        }
        Mode mode = parseMode(modeProperty);
        String userIdProperty = firstValue(
                args.getOptionValues(USER_ID_KEY), System.getProperty(USER_ID_KEY));
        if (userIdProperty == null) {
            throw new IllegalArgumentException("missing required argument: " + USER_ID_KEY);
        }
        return new Request(mode, UUID.fromString(userIdProperty));
    }

    /** Accepts the documented short forms as well as the enum constant names. */
    private static Mode parseMode(String rawMode) {
        String normalized = rawMode.trim().toUpperCase().replace('-', '_');
        return switch (normalized) {
            case "ISSUE", "ISSUE_REPLACEMENT" -> Mode.ISSUE_REPLACEMENT;
            case "REVOKE_ALL", "REVOKE" -> Mode.REVOKE_ALL;
            default -> throw new IllegalArgumentException(
                    MODE_KEY + " must be revoke-all or issue, was: " + rawMode);
        };
    }

    /** Shared logic for the CLI and the recovery test. */
    public String executeForTest(Mode mode, UUID userId) {
        return switch (mode) {
            case REVOKE_ALL -> {
                deviceSessionService.revokeAllForUser(userId);
                audit("REVOKE_ALL", userId);
                yield null;
            }
            case ISSUE_REPLACEMENT -> {
                String issued = deviceSessionService.issueReplacementSession(userId, coupleIdOf(userId));
                audit("ISSUE_REPLACEMENT", userId);
                yield issued;
            }
        };
    }

    private UUID coupleIdOf(UUID userId) {
        List<UUID> coupleIds = jdbcTemplate.queryForList(
                "SELECT couple_id FROM couple_member WHERE user_id = ? ORDER BY joined_at LIMIT 1",
                UUID.class, userId);
        if (coupleIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "user " + userId + " belongs to no couple space; nothing to recover");
        }
        return coupleIds.get(0);
    }

    private void audit(String action, UUID userId) {
        jdbcTemplate.update(
                "INSERT INTO session_admin_audit (id, action, target_user_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), action, userId);
    }

    private static String firstValue(List<String> optionValues, String systemProperty) {
        if (optionValues != null && !optionValues.isEmpty()) {
            String first = optionValues.get(0);
            if (first != null && !first.isBlank()) {
                return first;
            }
        }
        return systemProperty == null || systemProperty.isBlank() ? null : systemProperty;
    }
}
