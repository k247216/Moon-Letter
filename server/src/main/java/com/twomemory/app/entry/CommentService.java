package com.twomemory.app.entry;

import com.twomemory.app.auth.SpaceAccessPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class CommentService {

    private final JdbcTemplate jdbcTemplate;
    private final SpaceAccessPolicy accessPolicy;

    public CommentService(JdbcTemplate jdbcTemplate, SpaceAccessPolicy accessPolicy) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessPolicy = accessPolicy;
    }

    @Transactional
    public CommentView addComment(UUID entryId, UUID authorId, String body, UUID replyToId) {
        UUID coupleId = jdbcTemplate.queryForObject(
                "SELECT couple_id FROM entry WHERE id = ? AND deleted_at IS NULL",
                UUID.class, entryId);
        accessPolicy.requireMember(authorId, coupleId);
        String normalizedBody = validateBody(body);
        if (replyToId != null) {
            UUID parentEntry = jdbcTemplate.queryForObject(
                    "SELECT entry_id FROM comment WHERE id = ? AND deleted_at IS NULL",
                    UUID.class, replyToId);
            if (!entryId.equals(parentEntry)) {
                throw new EntryValidationException("reply must target a comment in the same entry");
            }
        }
        UUID commentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO comment(id, entry_id, author_id, body, reply_to_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, now(), now())
                """, commentId, entryId, authorId, normalizedBody, replyToId);
        return jdbcTemplate.queryForObject("""
                SELECT id, entry_id, author_id, body, reply_to_id, created_at
                FROM comment WHERE id = ?
                """, (rs, rowNum) -> new CommentView(
                rs.getObject("id", UUID.class),
                rs.getObject("entry_id", UUID.class),
                rs.getObject("author_id", UUID.class),
                rs.getString("body"),
                rs.getObject("reply_to_id", UUID.class),
                rs.getTimestamp("created_at").toInstant()), commentId);
    }

    static String validateBody(String raw) {
        if (raw == null) {
            throw new EntryValidationException("comment body is required");
        }
        String normalized = raw.trim();
        long codePoints = normalized.codePoints().count();
        if (codePoints < 1 || codePoints > 2_000) {
            throw new EntryValidationException("comment must contain 1 to 2000 Unicode code points");
        }
        return normalized;
    }
}
