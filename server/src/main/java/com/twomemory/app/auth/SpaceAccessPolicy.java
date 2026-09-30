package com.twomemory.app.auth;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class SpaceAccessPolicy {

    private final JdbcTemplate jdbcTemplate;

    public SpaceAccessPolicy(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void requireMember(UUID userId, UUID coupleId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM couple_member
                WHERE couple_id = ? AND user_id = ?
                  AND left_at IS NULL AND deleted_at IS NULL
                """, Integer.class, coupleId, userId);
        if (count == null || count != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "space access denied");
        }
    }

    public void requireActiveUser(UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_user WHERE id = ? AND status = 'ACTIVE'",
                Integer.class, userId);
        if (count == null || count != 1) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user is not active");
        }
    }
}
