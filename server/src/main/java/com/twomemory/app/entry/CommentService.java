package com.twomemory.app.entry;

import com.twomemory.app.auth.SpaceAccessPolicy;
import com.twomemory.app.sync.ChangeFeedService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CommentService {

    private final JdbcTemplate jdbcTemplate;
    private final SpaceAccessPolicy accessPolicy;
    private final ChangeFeedService changeFeedService;

    public CommentService(JdbcTemplate jdbcTemplate, SpaceAccessPolicy accessPolicy,
                          ChangeFeedService changeFeedService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessPolicy = accessPolicy;
        this.changeFeedService = changeFeedService;
    }

    @Transactional
    public CommentView addComment(UUID entryId, UUID authorId, String body, UUID replyToId) {
        return addComment(entryId, authorId, null, body, replyToId);
    }

    /**
     * Adds a comment. When commentId is supplied (sync path) it is adopted
     * verbatim so retries stay idempotent; a duplicate insert returns the
     * stored row. Every accepted comment appends a COMMENT change so the
     * partner's pull delivers it.
     */
    @Transactional
    public CommentView addComment(UUID entryId, UUID authorId, UUID commentId,
                                  String body, UUID replyToId) {
        EntryTarget target = jdbcTemplate.queryForObject("""
                SELECT couple_id, state::text AS state FROM entry
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> new EntryTarget(rs.getObject("couple_id", UUID.class),
                EntryState.valueOf(rs.getString("state"))), entryId);
        UUID coupleId = target.coupleId();
        accessPolicy.requireMember(authorId, coupleId);
        if (target.state() != EntryState.PUBLISHED) {
            throw new EntryValidationException("only a published record can be commented on");
        }
        String normalizedBody = validateBody(body);
        if (replyToId != null) {
            UUID parentEntry = jdbcTemplate.queryForObject(
                    "SELECT entry_id FROM comment WHERE id = ? AND deleted_at IS NULL",
                    UUID.class, replyToId);
            if (!entryId.equals(parentEntry)) {
                throw new EntryValidationException("reply must target a comment in the same entry");
            }
        }
        UUID id = commentId != null ? commentId : UUID.randomUUID();
        if (commentId != null) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM comment WHERE id = ?", Integer.class, commentId);
            if (exists != null && exists > 0) {
                return readComment(commentId);
            }
        }
        jdbcTemplate.update("""
                INSERT INTO comment(id, entry_id, author_id, body, reply_to_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, now(), now())
                """, id, entryId, authorId, normalizedBody, replyToId);
        CommentView view = readComment(id);
        changeFeedService.appendChange(coupleId, "COMMENT", entryId, "ADD", toJson(view));
        return view;
    }

    private CommentView readComment(UUID commentId) {
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

    private String toJson(CommentView view) {
        return "{\"entryId\":\"%s\",\"commentId\":\"%s\",\"authorId\":\"%s\",\"body\":%s,\"replyToId\":%s}"
                .formatted(view.entryId(), view.id(), view.authorId(),
                        quote(view.body()),
                        view.replyToId() == null ? "null" : "\"" + view.replyToId() + "\"");
    }

    private static String quote(String raw) {
        String escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    /** Space and visibility of the entry a comment targets. */
    private record EntryTarget(UUID coupleId, EntryState state) {
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
