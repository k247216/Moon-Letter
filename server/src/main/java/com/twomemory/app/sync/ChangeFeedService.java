package com.twomemory.app.sync;

import com.twomemory.app.auth.SpaceAccessPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ChangeFeedService {

    private final JdbcTemplate jdbcTemplate;
    private final SpaceAccessPolicy accessPolicy;

    public ChangeFeedService(JdbcTemplate jdbcTemplate, SpaceAccessPolicy accessPolicy) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessPolicy = accessPolicy;
    }

    @Transactional
    public long appendChange(UUID coupleId, String entityType, UUID entityId,
                             String operation, String payload) {
        if (coupleId == null || entityType == null || entityType.isBlank()
                || entityId == null || operation == null || operation.isBlank()) {
            throw new SyncValidationException("change identity and operation are required");
        }
        Long sequence = jdbcTemplate.queryForObject("""
                INSERT INTO sync_change(couple_id, entity_type, entity_id, operation, payload, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, now())
                RETURNING change_seq
                """, Long.class, coupleId, entityType, entityId, operation, payload);
        if (sequence == null) {
            throw new IllegalStateException("database did not return change sequence");
        }
        return sequence;
    }

    public ChangePage readChanges(UUID userId, UUID coupleId, long after, int limit) {
        accessPolicy.requireMember(userId, coupleId);
        return readChanges(coupleId, after, limit);
    }

    public ChangePage readChanges(UUID coupleId, long after, int limit) {
        if (after < 0 || limit < 1 || limit > 200) {
            throw new SyncValidationException("after must be non-negative and limit must be between 1 and 200");
        }
        List<ChangeView> raw = jdbcTemplate.query("""
                SELECT change_seq, entity_type, entity_id, operation,
                       payload::text AS payload, created_at
                FROM sync_change
                WHERE couple_id = ? AND change_seq > ?
                ORDER BY change_seq
                LIMIT ?
                """, (rs, rowNum) -> new ChangeView(
                rs.getLong("change_seq"),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                rs.getString("operation"),
                rs.getString("payload"),
                rs.getTimestamp("created_at").toInstant()), coupleId, after, limit + 1);
        boolean hasMore = raw.size() > limit;
        List<ChangeView> changes = hasMore ? raw.subList(0, limit) : raw;
        long next = changes.isEmpty() ? after : changes.get(changes.size() - 1).sequence();
        return new ChangePage(List.copyOf(changes), next, hasMore);
    }
}
