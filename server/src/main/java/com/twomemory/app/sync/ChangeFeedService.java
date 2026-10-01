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

    /**
     * Allocates the next per-couple sequence under a lock on the couple's
     * sync state row, inside the caller's transaction. Concurrent mutations
     * for the same couple serialize, so the sequence order matches commit
     * order and no committed change can be skipped by a paging client.
     */
    @Transactional
    public long appendChange(UUID coupleId, String entityType, UUID entityId,
                             String operation, String payload) {
        if (coupleId == null || entityType == null || entityType.isBlank()
                || entityId == null || operation == null || operation.isBlank()) {
            throw new SyncValidationException("change identity and operation are required");
        }
        jdbcTemplate.update("""
                INSERT INTO couple_sync_state(couple_id, last_space_sequence)
                VALUES (?, 0)
                ON CONFLICT (couple_id) DO NOTHING
                """, coupleId);
        Long sequence = jdbcTemplate.queryForObject("""
                UPDATE couple_sync_state
                SET last_space_sequence = last_space_sequence + 1
                WHERE couple_id = ?
                RETURNING last_space_sequence
                """, Long.class, coupleId);
        if (sequence == null) {
            throw new IllegalStateException("couple sync state row went missing");
        }
        jdbcTemplate.update("""
                INSERT INTO sync_change(couple_id, space_sequence, entity_type, entity_id,
                                        operation, payload, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, now())
                """, coupleId, sequence, entityType, entityId, operation, payload);
        return sequence;
    }

    /**
     * High-water sequence for the couple. Used when the mutation itself appended
     * the change row, so the wake-up notification still reports a real position.
     */
    public long lastSequence(UUID coupleId) {
        return jdbcTemplate.query("""
                SELECT last_space_sequence FROM couple_sync_state WHERE couple_id = ?
                """, (rs, rowNum) -> rs.getLong(1), coupleId)
                .stream().findFirst().orElse(0L);
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
                SELECT space_sequence, entity_type, entity_id, operation,
                       payload::text AS payload, created_at
                FROM sync_change
                WHERE couple_id = ? AND space_sequence > ?
                ORDER BY space_sequence
                LIMIT ?
                """, (rs, rowNum) -> new ChangeView(
                rs.getLong("space_sequence"),
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
