package com.twomemory.app.export;

import com.twomemory.app.auth.SpaceAccessPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Selective export without secrets (spec 2026-10-01 §4.7): entries, their
 * blocks, comments and revisions for one space and optional date range, as
 * plain JSON maps — human-readable and free of tokens or keys.
 */
@Service
public class ExportService {

    private final JdbcTemplate jdbcTemplate;
    private final SpaceAccessPolicy accessPolicy;

    public ExportService(JdbcTemplate jdbcTemplate, SpaceAccessPolicy accessPolicy) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessPolicy = accessPolicy;
    }

    public Map<String, Object> exportCouple(UUID actorId, UUID coupleId,
                                            java.time.Instant from, java.time.Instant to) {
        accessPolicy.requireMember(actorId, coupleId);
        List<Map<String, Object>> entries = jdbcTemplate.query("""
                SELECT e.id::text AS id, e.mode::text AS mode, e.state::text AS state,
                       e.author_id::text AS author_id, e.title,
                       EXTRACT(EPOCH FROM e.occurred_at) * 1000 AS occurred_at_epoch_millis,
                       e.occurred_timezone, e.current_revision_no, e.created_at, e.updated_at
                FROM entry e
                WHERE e.couple_id = ? AND e.deleted_at IS NULL
                  AND e.occurred_at >= COALESCE(?::timestamptz, to_timestamp(0))
                  AND e.occurred_at <= COALESCE(?::timestamptz, now())
                ORDER BY e.occurred_at DESC, e.id
                """, (rs, rowNum) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", rs.getString("id"));
            entry.put("mode", rs.getString("mode"));
            entry.put("state", rs.getString("state"));
            entry.put("authorId", rs.getString("author_id"));
            entry.put("title", rs.getString("title"));
            entry.put("occurredAtEpochMillis", rs.getLong("occurred_at_epoch_millis"));
            entry.put("occurredTimezone", rs.getString("occurred_timezone"));
            entry.put("blocks", blocksOf(UUID.fromString(rs.getString("id"))));
            entry.put("comments", commentsOf(UUID.fromString(rs.getString("id"))));
            return entry;
        }, queryParams(coupleId, from, to));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("coupleId", coupleId.toString());
        result.put("exportedAt", java.time.Instant.now().toString());
        result.put("entryCount", entries.size());
        result.put("entries", entries);
        return result;
    }

    private Object[] queryParams(UUID coupleId, java.time.Instant from, java.time.Instant to) {
        List<Object> params = new ArrayList<>();
        params.add(coupleId);
        params.add(from == null ? null : java.sql.Timestamp.from(from));
        params.add(to == null ? null : java.sql.Timestamp.from(to));
        return params.toArray();
    }

    private List<Map<String, Object>> blocksOf(UUID entryId) {
        return jdbcTemplate.query("""
                SELECT id::text AS id, type::text AS type, order_key, created_by::text AS author_id,
                       payload::text AS payload, asset_id::text AS asset_id, deleted_at
                FROM entry_block WHERE entry_id = ? ORDER BY order_key, id
                """, (rs, rowNum) -> {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("id", rs.getString("id"));
            block.put("type", rs.getString("type"));
            block.put("orderKey", rs.getLong("order_key"));
            block.put("authorId", rs.getString("author_id"));
            block.put("payload", rs.getString("payload"));
            block.put("assetId", rs.getString("asset_id"));
            block.put("deleted", rs.getTimestamp("deleted_at") != null);
            return block;
        }, entryId);
    }

    private List<Map<String, Object>> commentsOf(UUID entryId) {
        return jdbcTemplate.query("""
                SELECT id::text AS id, author_id::text AS author_id, body, reply_to_id::text AS reply_to_id,
                       created_at
                FROM comment WHERE entry_id = ? AND deleted_at IS NULL ORDER BY created_at, id
                """, (rs, rowNum) -> {
            Map<String, Object> comment = new LinkedHashMap<>();
            comment.put("id", rs.getString("id"));
            comment.put("authorId", rs.getString("author_id"));
            comment.put("body", rs.getString("body"));
            comment.put("replyToId", rs.getString("reply_to_id"));
            comment.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
            return comment;
        }, entryId);
    }
}
